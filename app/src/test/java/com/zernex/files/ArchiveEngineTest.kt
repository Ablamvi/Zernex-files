package com.zernex.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveEngineTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun sourceTree(): File {
        val root = File(temporary.root, "sources").apply { mkdirs() }
        File(root, "hello.txt").writeText("ZERNEX archive test")
        File(root, "nested").mkdir()
        File(root, "nested/data.json").writeText("{\"ok\":true}")
        return root
    }

    private fun assertTreeExtracted(destination: File) {
        assertEquals("ZERNEX archive test", File(destination, "sources/hello.txt").readText())
        assertEquals("{\"ok\":true}", File(destination, "sources/nested/data.json").readText())
    }

    @Test
    fun zipRoundTripAndListing() {
        val source = sourceTree()
        val archive = ArchiveEngine.create(
            listOf(source),
            temporary.root,
            "roundtrip",
            ArchiveFormat.ZIP
        )
        assertTrue(archive.isFile)
        assertTrue(ArchiveEngine.list(archive).any { it.name == "sources/hello.txt" })
        val destination = File(temporary.root, "zip-output")
        assertEquals(4, ArchiveEngine.extractSafely(archive, destination))
        assertTreeExtracted(destination)
    }

    @Test
    fun aes256ZipRequiresPasswordAndExtractsWithCorrectPassword() {
        val source = File(temporary.root, "secret.txt").apply { writeText("private payload") }
        val archive = ArchiveEngine.create(
            listOf(source),
            temporary.root,
            "encrypted",
            ArchiveFormat.ZIP,
            "correct-horse-42"
        )

        val wrongDestination = File(temporary.root, "wrong-password")
        val error = runCatching { ArchiveEngine.extractSafely(archive, wrongDestination) }.exceptionOrNull()
        assertTrue(error != null)
        assertTrue(wrongDestination.listFiles().isNullOrEmpty())

        val destination = File(temporary.root, "right-password")
        assertEquals(1, ArchiveEngine.extractSafely(archive, destination, "correct-horse-42"))
        assertEquals("private payload", File(destination, "secret.txt").readText())
    }

    @Test
    fun sevenZipRoundTrip() {
        val source = File(temporary.root, "seven-source").apply { mkdirs() }
        File(source, "payload.bin").writeBytes(ByteArray(8192) { (it % 251).toByte() })
        val archive = ArchiveEngine.create(
            listOf(source),
            temporary.root,
            "roundtrip-7z",
            ArchiveFormat.SEVEN_Z
        )
        assertTrue(ArchiveEngine.list(archive).any { it.name == "seven-source/payload.bin" })
        val destination = File(temporary.root, "seven-output")
        ArchiveEngine.extractSafely(archive, destination)
        assertTrue(File(destination, "seven-source/payload.bin").readBytes().contentEquals(File(source, "payload.bin").readBytes()))
    }

    @Test
    fun tarGzipRoundTrip() {
        val source = File(temporary.root, "tar-source").apply { mkdirs() }
        File(source, "payload.txt").writeText("tar gzip payload")
        val archive = ArchiveEngine.create(
            listOf(source),
            temporary.root,
            "roundtrip-tgz",
            ArchiveFormat.TAR_GZ
        )
        assertTrue(ArchiveEngine.list(archive).any { it.name == "tar-source/payload.txt" })
        val destination = File(temporary.root, "tar-output")
        ArchiveEngine.extractSafely(archive, destination)
        assertEquals("tar gzip payload", File(destination, "tar-source/payload.txt").readText())
    }

    @Test
    fun zipSlipEntryIsRejectedAndDoesNotWriteOutsideDestination() {
        val archive = File(temporary.root, "malicious.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../escape.txt"))
            zip.write("must not escape".toByteArray())
            zip.closeEntry()
        }
        val destination = File(temporary.root, "safe-output")
        val result = runCatching { ArchiveEngine.extractSafely(archive, destination) }
        assertTrue(result.isFailure)
        assertFalse(File(temporary.root, "escape.txt").exists())
        assertTrue(destination.listFiles().isNullOrEmpty())
    }
    @Test
    fun zipExtractsNestedFilesWithoutExplicitDirectoryEntries() {
        val archive = File(temporary.root, "implicit-directories.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("one/two/three.txt"))
            zip.write("nested payload".toByteArray())
            zip.closeEntry()
        }

        val destination = File(temporary.root, "implicit-output")
        assertEquals(1, ArchiveEngine.extractSafely(archive, destination))
        assertEquals("nested payload", File(destination, "one/two/three.txt").readText())
    }

    @Test
    fun zipAbsoluteAndWindowsDrivePathsAreRejected() {
        listOf("/absolute-escape.txt", "C:/windows-escape.txt").forEachIndexed { index, entryName ->
            val archive = File(temporary.root, "absolute-$index.zip")
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write("must not escape".toByteArray())
                zip.closeEntry()
            }

            val destination = File(temporary.root, "absolute-output-$index")
            assertTrue(runCatching { ArchiveEngine.extractSafely(archive, destination) }.isFailure)
            assertTrue(destination.listFiles().isNullOrEmpty())
        }
        assertFalse(File(temporary.root, "absolute-escape.txt").exists())
    }

    @Test
    fun extractionRefusesNonEmptyDestinationWithoutChangingIt() {
        val archive = File(temporary.root, "safe.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("new.txt"))
            zip.write("new".toByteArray())
            zip.closeEntry()
        }
        val destination = File(temporary.root, "non-empty").apply { mkdirs() }
        val existing = File(destination, "keep.txt").apply { writeText("keep me") }

        assertTrue(runCatching { ArchiveEngine.extractSafely(archive, destination) }.isFailure)
        assertEquals("keep me", existing.readText())
        assertFalse(File(destination, "new.txt").exists())
    }

}
