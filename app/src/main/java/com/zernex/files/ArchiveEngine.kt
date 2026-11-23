package com.zernex.files

import net.lingala.zip4j.ZipFile as Zip4jFile
import net.lingala.zip4j.io.outputstream.ZipOutputStream as Zip4jOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

internal enum class ArchiveFormat(val label: String, val extension: String) {
    ZIP("ZIP", ".zip"),
    SEVEN_Z("7Z", ".7z"),
    TAR("TAR", ".tar"),
    TAR_GZ("TAR.GZ", ".tar.gz"),
    TAR_BZ2("TAR.BZ2", ".tar.bz2"),
    TAR_XZ("TAR.XZ", ".tar.xz")
}

internal data class ArchiveEntryInfo(
    val name: String,
    val size: Long,
    val isDirectory: Boolean
)

/**
 * Archive engine shared by the local file browser.
 *
 * Security rules are deliberately strict: no absolute paths, traversal,
 * links, duplicate entries, silent overwrites, or unbounded extraction.
 * ZIP uses AES-256 when a password is supplied. 7z/TAR variants are currently
 * unencrypted; TAR has no built-in password protection.
 */
internal object ArchiveEngine {
    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_ENTRIES = 10_000
    private const val MAX_FILE_BYTES = 256L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 512L * 1024L * 1024L
    private const val MAX_SOURCE_ENTRIES = 50_000
    private const val MAX_SOURCE_BYTES = 8L * 1024L * 1024L * 1024L

    private enum class Kind { ZIP, SEVEN_Z, TAR, TAR_GZ, TAR_BZ2, TAR_XZ, GZIP, BZIP2, XZ }

    fun isSupportedName(name: String): Boolean = kindFor(name) != null

    fun list(file: File): List<ArchiveEntryInfo> {
        require(file.isFile && file.canRead()) { "Archive inaccessible ou illisible" }
        return when (kindFor(file.name) ?: error("Format d'archive non pris en charge")) {
            Kind.ZIP -> {
                val zip = Zip4jFile(file)
                zip.fileHeaders.map { header ->
                    ArchiveEntryInfo(header.fileName, header.uncompressedSize, header.isDirectory)
                }
            }
            Kind.SEVEN_Z -> openSevenZ(file, null).use { seven ->
                buildList {
                    while (true) {
                        val entry = seven.nextEntry ?: break
                        add(ArchiveEntryInfo(entry.name, entry.size, entry.isDirectory))
                    }
                }
            }
            Kind.TAR, Kind.TAR_GZ, Kind.TAR_BZ2, Kind.TAR_XZ -> openTar(file, kindFor(file.name)!!).use { tar ->
                buildList {
                    while (true) {
                        val entry = tar.nextTarEntry ?: break
                        add(ArchiveEntryInfo(entry.name, entry.size, entry.isDirectory))
                    }
                }
            }
            Kind.GZIP, Kind.BZIP2, Kind.XZ -> listOf(
                ArchiveEntryInfo(stripSingleCompressionSuffix(file.name), -1L, false)
            )
        }.sortedWith(compareBy<ArchiveEntryInfo> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
    }

    fun create(
        sources: List<File>,
        destinationDirectory: File,
        baseName: String,
        format: ArchiveFormat,
        password: String? = null
    ): File {
        require(sources.isNotEmpty()) { "Sélectionne au moins un fichier ou dossier" }
        require(baseName.isNotBlank()) { "Le nom de l'archive est obligatoire" }
        val cleanBase = baseName.trim()
        require(cleanBase != "." && cleanBase != ".." && !cleanBase.contains('/') && !cleanBase.contains('\\') && !cleanBase.contains('\u0000')) {
            "Nom d'archive invalide"
        }
        require(destinationDirectory.exists() || destinationDirectory.mkdirs()) { "Dossier de destination inaccessible" }
        require(destinationDirectory.isDirectory && destinationDirectory.canWrite()) { "Dossier de destination non inscriptible" }

        val destination = destinationDirectory.canonicalFile
        val canonicalSources = sources.map { source ->
            require(source.exists() && source.canRead()) { "Source inaccessible : ${source.name}" }
            val canonical = source.canonicalFile
            require(source.absoluteFile == canonical) { "Les liens symboliques ne sont pas ajoutés aux archives : ${source.name}" }
            require(!(canonical.isDirectory && (destination == canonical || destination.path.startsWith(canonical.path + File.separator)))) {
                "L'archive ne peut pas être créée dans un dossier source"
            }
            canonical
        }
        require(canonicalSources.distinct().size == canonicalSources.size) { "La sélection contient des doublons" }
        if (format == ArchiveFormat.ZIP && !password.isNullOrEmpty()) {
            require(password.length >= 8) { "Le mot de passe ZIP doit contenir au moins 8 caractères" }
        } else {
            require(password.isNullOrEmpty() || format == ArchiveFormat.ZIP) {
                "Le chiffrement par mot de passe est actuellement disponible uniquement pour ZIP"
            }
        }

        val finalFile = File(destination, cleanBase + format.extension)
        require(!finalFile.exists()) { "Un fichier porte déjà ce nom : ${finalFile.name}" }
        val tempFile = File(destination, ".${finalFile.name}.zernex-part")
        require(!tempFile.exists() || tempFile.delete()) { "Un fichier temporaire bloque la création" }

        try {
            when (format) {
                ArchiveFormat.ZIP -> createZip(canonicalSources, tempFile, password)
                ArchiveFormat.SEVEN_Z -> createSevenZ(canonicalSources, tempFile)
                ArchiveFormat.TAR -> createTar(canonicalSources, tempFile, null)
                ArchiveFormat.TAR_GZ -> createTar(canonicalSources, tempFile, Kind.TAR_GZ)
                ArchiveFormat.TAR_BZ2 -> createTar(canonicalSources, tempFile, Kind.TAR_BZ2)
                ArchiveFormat.TAR_XZ -> createTar(canonicalSources, tempFile, Kind.TAR_XZ)
            }
            require(tempFile.isFile && tempFile.length() > 0L) { "L'archive générée est vide" }
            require(tempFile.renameTo(finalFile)) { "Impossible de finaliser l'archive" }
            return finalFile
        } catch (error: Throwable) {
            tempFile.delete()
            throw error
        }
    }

    fun extractSafely(archive: File, destination: File, password: String? = null): Int {
        require(archive.isFile && archive.canRead()) { "Archive inaccessible ou illisible" }
        val root = destination.canonicalFile
        require(root != archive.canonicalFile) { "Destination invalide" }
        require(!root.exists() || (root.isDirectory && root.listFiles().isNullOrEmpty())) {
            "Le dossier de destination doit être vide"
        }
        val rootWasCreated = !root.exists()
        require(root.exists() || root.mkdirs()) { "Impossible de créer le dossier de destination" }

        val writer = SafeExtractWriter(root)
        try {
            val kind = kindFor(archive.name) ?: error("Format d'archive non pris en charge")
            when (kind) {
                Kind.ZIP -> extractZip(archive, password, writer)
                Kind.SEVEN_Z -> extractSevenZ(archive, password, writer)
                Kind.TAR, Kind.TAR_GZ, Kind.TAR_BZ2, Kind.TAR_XZ -> extractTar(archive, kind, writer)
                Kind.GZIP, Kind.BZIP2, Kind.XZ -> extractSingleCompressedFile(archive, kind, writer)
            }
            return writer.entryCount
        } catch (error: Throwable) {
            writer.rollback()
            if (rootWasCreated && root.exists() && root.listFiles().isNullOrEmpty()) root.delete()
            throw error
        }
    }

    private fun createZip(sources: List<File>, target: File, password: String?) {
        val zip = Zip4jOutputStream(FileOutputStream(target), password?.takeIf { it.isNotEmpty() }?.toCharArray())
        val names = HashSet<String>()
        var visited = 0
        var sourceBytes = 0L
        zip.use { output ->
            fun add(file: File, archivePath: String, depth: Int) {
                require(depth <= 64) { "Dossier trop profond : ${file.name}" }
                require(file.absoluteFile == file.canonicalFile) { "Lien symbolique refusé : ${file.name}" }
                visited++
                require(visited <= MAX_SOURCE_ENTRIES) { "Trop d'éléments à compresser (maximum $MAX_SOURCE_ENTRIES)" }
                val entryName = archivePath.replace('\\', '/')
                require(entryName.isNotBlank() && !entryName.startsWith('/') && !entryName.split('/').any { it == ".." || it == "." }) {
                    "Chemin source invalide : $entryName"
                }
                val key = entryName.lowercase(Locale.ROOT)
                require(names.add(key)) { "Nom en double dans la sélection : $entryName" }
                val parameters = ZipParameters().apply {
                    fileNameInZip = if (file.isDirectory) "$entryName/" else entryName
                    compressionMethod = CompressionMethod.DEFLATE
                    compressionLevel = CompressionLevel.NORMAL
                    isEncryptFiles = !password.isNullOrEmpty() && file.isFile
                    if (isEncryptFiles) {
                        encryptionMethod = EncryptionMethod.AES
                        aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                    }
                }
                output.putNextEntry(parameters)
                if (file.isFile) {
                    file.inputStream().buffered().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            sourceBytes += count.toLong()
                            require(sourceBytes <= MAX_SOURCE_BYTES) { "La sélection dépasse 8 Gio" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                output.closeEntry()
                if (file.isDirectory) {
                    val children = file.listFiles()?.sortedBy { it.name.lowercase(Locale.ROOT) }
                        ?: error("Dossier illisible : ${file.name}")
                    children.forEach { child -> add(child, "$entryName/${child.name}", depth + 1) }
                }
            }
            sources.forEach { source -> add(source, source.name, 0) }
        }
    }

    private fun createSevenZ(sources: List<File>, target: File) {
        val output = SevenZOutputFile(target)
        val names = HashSet<String>()
        var visited = 0
        var sourceBytes = 0L
        output.use { seven ->
            fun add(file: File, archivePath: String, depth: Int) {
                require(depth <= 64) { "Dossier trop profond : ${file.name}" }
                require(file.absoluteFile == file.canonicalFile) { "Lien symbolique refusé : ${file.name}" }
                visited++
                require(visited <= MAX_SOURCE_ENTRIES) { "Trop d'éléments à compresser (maximum $MAX_SOURCE_ENTRIES)" }
                val key = archivePath.lowercase(Locale.ROOT)
                require(names.add(key)) { "Nom en double dans la sélection : $archivePath" }
                val entry = seven.createArchiveEntry(file, archivePath)
                seven.putArchiveEntry(entry)
                if (file.isFile) {
                    file.inputStream().buffered().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            sourceBytes += count.toLong()
                            require(sourceBytes <= MAX_SOURCE_BYTES) { "La sélection dépasse 8 Gio" }
                            seven.write(buffer, 0, count)
                        }
                    }
                }
                seven.closeArchiveEntry()
                if (file.isDirectory) {
                    val children = file.listFiles()?.sortedBy { it.name.lowercase(Locale.ROOT) }
                        ?: error("Dossier illisible : ${file.name}")
                    children.forEach { child -> add(child, "$archivePath/${child.name}", depth + 1) }
                }
            }
            sources.forEach { source -> add(source, source.name, 0) }
        }
    }

    private fun createTar(sources: List<File>, target: File, compressedKind: Kind?) {
        val raw = BufferedOutputStream(FileOutputStream(target), BUFFER_SIZE)
        val compressed: OutputStream = when (compressedKind) {
            Kind.TAR_GZ -> GzipCompressorOutputStream(raw)
            Kind.TAR_BZ2 -> BZip2CompressorOutputStream(raw)
            Kind.TAR_XZ -> XZCompressorOutputStream(raw)
            else -> raw
        }
        val tar = TarArchiveOutputStream(compressed)
        tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
        tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
        val names = HashSet<String>()
        var visited = 0
        var sourceBytes = 0L
        tar.use { output ->
            fun add(file: File, archivePath: String, depth: Int) {
                require(depth <= 64) { "Dossier trop profond : ${file.name}" }
                require(file.absoluteFile == file.canonicalFile) { "Lien symbolique refusé : ${file.name}" }
                visited++
                require(visited <= MAX_SOURCE_ENTRIES) { "Trop d'éléments à compresser (maximum $MAX_SOURCE_ENTRIES)" }
                require(names.add(archivePath.lowercase(Locale.ROOT))) { "Nom en double dans la sélection : $archivePath" }
                val entry = TarArchiveEntry(file, archivePath)
                output.putArchiveEntry(entry)
                if (file.isFile) {
                    file.inputStream().buffered().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            sourceBytes += count.toLong()
                            require(sourceBytes <= MAX_SOURCE_BYTES) { "La sélection dépasse 8 Gio" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                output.closeArchiveEntry()
                if (file.isDirectory) {
                    val children = file.listFiles()?.sortedBy { it.name.lowercase(Locale.ROOT) }
                        ?: error("Dossier illisible : ${file.name}")
                    children.forEach { child -> add(child, "$archivePath/${child.name}", depth + 1) }
                }
            }
            sources.forEach { source -> add(source, source.name, 0) }
            output.finish()
        }
    }

    private fun extractZip(archive: File, password: String?, writer: SafeExtractWriter) {
        val zip = if (password.isNullOrEmpty()) Zip4jFile(archive) else Zip4jFile(archive, password.toCharArray())
        zip.fileHeaders.forEach { header ->
            if (header.isDirectory) {
                writer.createDirectory(header.fileName)
            } else {
                if (header.isEncrypted && password.isNullOrEmpty()) {
                    error("Cette archive ZIP est protégée. Saisis son mot de passe pour l'extraire.")
                }
                zip.getInputStream(header).use { input -> writer.writeFile(header.fileName, input) }
            }
        }
    }

    private fun extractSevenZ(archive: File, password: String?, writer: SafeExtractWriter) {
        val builder = SevenZFile.builder().setFile(archive)
        if (!password.isNullOrEmpty()) builder.setPassword(password.toCharArray())
        builder.get().use { seven ->
            while (true) {
                val entry = seven.nextEntry ?: break
                if (entry.isDirectory) {
                    writer.createDirectory(entry.name)
                } else {
                    writer.writeFile(entry.name) { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val count = seven.read(buffer)
                            if (count < 0) break
                            writer.writeChunk(output, buffer, count)
                        }
                    }
                }
            }
        }
    }

    private fun extractTar(archive: File, kind: Kind, writer: SafeExtractWriter) {
        openTar(archive, kind).use { tar ->
            while (true) {
                val entry = tar.nextTarEntry ?: break
                require(!entry.isSymbolicLink && !entry.isLink && !entry.isCharacterDevice && !entry.isBlockDevice && !entry.isFIFO) {
                    "Lien ou fichier spécial refusé dans l'archive : ${entry.name}"
                }
                if (entry.isDirectory) {
                    writer.createDirectory(entry.name)
                } else if (entry.isFile) {
                    writer.writeFile(entry.name, tar)
                } else {
                    error("Type d'entrée TAR non pris en charge : ${entry.name}")
                }
            }
        }
    }

    private fun extractSingleCompressedFile(archive: File, kind: Kind, writer: SafeExtractWriter) {
        val name = stripSingleCompressionSuffix(archive.name)
        val input = openCompressor(BufferedInputStream(FileInputStream(archive)), kind)
        input.use { writer.writeFile(name, it) }
    }

    private fun openSevenZ(file: File, password: String?): SevenZFile {
        val builder = SevenZFile.builder().setFile(file)
        if (!password.isNullOrEmpty()) builder.setPassword(password.toCharArray())
        return builder.get()
    }

    private fun openTar(file: File, kind: Kind): TarArchiveInputStream {
        val raw = BufferedInputStream(FileInputStream(file), BUFFER_SIZE)
        val decoded = when (kind) {
            Kind.TAR_GZ -> GzipCompressorInputStream(raw)
            Kind.TAR_BZ2 -> BZip2CompressorInputStream(raw)
            Kind.TAR_XZ -> XZCompressorInputStream(raw)
            else -> raw
        }
        return TarArchiveInputStream(decoded)
    }

    private fun openCompressor(input: InputStream, kind: Kind): InputStream = when (kind) {
        Kind.GZIP -> GzipCompressorInputStream(input)
        Kind.BZIP2 -> BZip2CompressorInputStream(input)
        Kind.XZ -> XZCompressorInputStream(input)
        else -> error("Compresseur non pris en charge")
    }

    private fun kindFor(name: String): Kind? {
        val lower = name.lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> Kind.TAR_GZ
            lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") || lower.endsWith(".tbz") -> Kind.TAR_BZ2
            lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> Kind.TAR_XZ
            lower.endsWith(".zip") -> Kind.ZIP
            lower.endsWith(".7z") -> Kind.SEVEN_Z
            lower.endsWith(".tar") -> Kind.TAR
            lower.endsWith(".gz") -> Kind.GZIP
            lower.endsWith(".bz2") -> Kind.BZIP2
            lower.endsWith(".xz") -> Kind.XZ
            else -> null
        }
    }

    private fun stripSingleCompressionSuffix(name: String): String =
        name.substringBeforeLast('.', name).ifBlank { "extracted" }

    private class SafeExtractWriter(private val root: File) {
        private val created = ArrayList<File>()
        private val seen = HashSet<String>()
        private var totalBytes = 0L
        var entryCount: Int = 0
            private set

        fun createDirectory(rawName: String) {
            val (relative, target) = resolve(rawName, isDirectory = true)
            register(relative)
            require(!target.exists() || target.isDirectory) { "Conflit de dossier : $relative" }
            if (!target.exists()) {
                require(target.mkdirs()) { "Création du dossier impossible : $relative" }
                created.add(target)
            }
            entryCount++
        }

        fun writeFile(rawName: String, input: InputStream) {
            writeFile(rawName) { output -> copyBounded(input, output) }
        }

        fun writeFile(rawName: String, write: (OutputStream) -> Unit) {
            val (relative, target) = resolve(rawName, isDirectory = false)
            register(relative)
            require(!target.exists()) { "Un élément existe déjà : $relative" }
            val parent = target.parentFile ?: error("Dossier parent invalide")
            ensureParentDirectories(parent)
            require(target.createNewFile()) { "Création du fichier impossible : $relative" }
            created.add(target)
            try {
                BufferedOutputStream(FileOutputStream(target), BUFFER_SIZE).use(write)
            } catch (error: Throwable) {
                target.delete()
                throw error
            }
            entryCount++
        }

        fun writeChunk(output: OutputStream, buffer: ByteArray, count: Int) {
            // Called by the 7z decoder, whose input is not exposed as an InputStream.
            // The active output file is accounted by the thread-local limits below.
            currentEntryBytes += count
            totalBytes += count
            require(currentEntryBytes <= MAX_FILE_BYTES) { "Un fichier dépasse 256 Mio" }
            require(totalBytes <= MAX_TOTAL_BYTES) { "Limite totale d'extraction de 512 Mio dépassée" }
            output.write(buffer, 0, count)
        }

        private var currentEntryBytes = 0L

        private fun copyBounded(input: InputStream, output: OutputStream) {
            currentEntryBytes = 0L
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                currentEntryBytes += count.toLong()
                totalBytes += count.toLong()
                require(currentEntryBytes <= MAX_FILE_BYTES) { "Un fichier dépasse 256 Mio" }
                require(totalBytes <= MAX_TOTAL_BYTES) { "Limite totale d'extraction de 512 Mio dépassée" }
                output.write(buffer, 0, count)
            }
            currentEntryBytes = 0L
        }

        private fun resolve(rawName: String, isDirectory: Boolean): Pair<String, File> {
            require(rawName.isNotBlank() && !rawName.contains('\u0000')) { "Nom d'entrée vide ou invalide" }
            val normalized = rawName.replace('\\', '/')
            require(!normalized.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(normalized)) {
                "Chemin absolu interdit dans l'archive"
            }
            val parts = normalized.split('/').filter { it.isNotEmpty() }
            require(parts.isNotEmpty() && parts.none { it == "." || it == ".." || it.contains(':') }) {
                "Chemin dangereux dans l'archive : $rawName"
            }
            val relative = parts.joinToString("/")
            val target = File(root, parts.joinToString(File.separator)).canonicalFile
            require(target.path.startsWith(root.path + File.separator)) { "Chemin d'extraction dangereux : $rawName" }
            return (if (isDirectory) "$relative/" else relative) to target
        }

        private fun register(relative: String) {
            require(seen.add(relative.lowercase(Locale.ROOT))) { "Entrée en double : $relative" }
            require(entryCount < MAX_ENTRIES) { "Trop d'entrées dans l'archive (maximum $MAX_ENTRIES)" }
        }

        private fun ensureParentDirectories(parent: File) {
            val missing = ArrayList<File>()
            var cursor: File? = parent
            while (cursor != null && cursor != root && !cursor.exists()) {
                missing.add(cursor)
                cursor = cursor.parentFile
            }
            require(cursor == root || cursor == null) { "Dossier parent hors destination" }
            require(missing.none { it.exists() && !it.isDirectory }) { "Conflit de type dans le chemin extrait" }
            missing.asReversed().forEach { directory ->
                require(directory.mkdirs() || directory.isDirectory) { "Création de dossier impossible" }
                created.add(directory)
            }
            require(parent.isDirectory) { "Dossier parent invalide" }
        }

        fun rollback() {
            created.asReversed().forEach { file ->
                runCatching { if (file.isDirectory) file.deleteRecursively() else file.delete() }
            }
            created.clear()
        }
    }
}
