package com.zernex.files

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.security.KeyStore
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.util.Locale

/** Formate une taille de fichier avec des unités binaires lisibles. */
private fun formatSize(bytes: Long): String {
    if (bytes < 0L) return "Taille inconnue"
    if (bytes < 1024L) return "$bytes octet${if (bytes > 1L) "s" else ""}"

    val units = arrayOf("Ko", "Mo", "Go", "To", "Po", "Eo")
    var value = bytes.toDouble()
    var unitIndex = -1
    do {
        value /= 1024.0
        unitIndex++
    } while (value >= 1024.0 && unitIndex < units.lastIndex)

    return String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
}

private enum class BrowserLayout { LIST, GRID }
private enum class FileSort { NAME, SIZE, DATE, TYPE }

@Composable
fun AdvancedFileManagerScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("zernex_browser", Context.MODE_PRIVATE) }
    var treeUriString by remember { mutableStateOf(prefs.getString("saf_tree_uri", null)) }
    // Le SAF n'est plus imposé lorsque la variante Android 10 Legacy peut
    // utiliser le stockage partagé directement.
    val mustUseSaf = Build.VERSION.SDK_INT == 29 && !BuildConfig.LEGACY_STORAGE
    var useSafMode by remember { mutableStateOf(mustUseSaf || prefs.getBoolean("use_saf_mode_1_0", false)) }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                prefs.edit().putString("saf_tree_uri", uri.toString()).putBoolean("use_saf_mode", true).putBoolean("use_saf_mode_1_0", true).apply()
                treeUriString = uri.toString()
                useSafMode = true
            }.onFailure {
                android.widget.Toast.makeText(context, "Autorisation du dossier non conservée : ${it.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    val pickSafFolder: () -> Unit = {
        useSafMode = true
        prefs.edit().putBoolean("use_saf_mode", true).putBoolean("use_saf_mode_1_0", true).apply()
        treePicker.launch(treeUriString?.let { Uri.parse(it) })
    }

    if (mustUseSaf || useSafMode) {
        val savedTree = treeUriString?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (savedTree == null) {
            SafAccessScreen(onPickFolder = pickSafFolder, onExit = {
                if (!mustUseSaf) {
                    useSafMode = false
                    prefs.edit().putBoolean("use_saf_mode", false).putBoolean("use_saf_mode_1_0", false).apply()
                }
            }, showBack = !mustUseSaf)
        } else {
            SafFileManagerScreen(
                treeUri = savedTree,
                onPickAnotherFolder = pickSafFolder,
                onExit = {
                    if (!mustUseSaf) {
                        useSafMode = false
                        prefs.edit().putBoolean("use_saf_mode", false).putBoolean("use_saf_mode_1_0", false).apply()
                    }
                }
            )
        }
    } else {
        LocalFileManagerScreen(onChooseSaf = pickSafFolder)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun LocalFileManagerScreen(onChooseSaf: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("zernex_browser", Context.MODE_PRIVATE) }
    // Démarre dans le volume partagé principal : la racine Linux / n'est pas
    // une racine de stockage utilisateur et ses sous-répertoires sont protégés.
    // L'accès aux autres volumes se fait via leurs points de montage autorisés.
    val sharedStorageRoot = remember { Environment.getExternalStorageDirectory().absolutePath }
    val initial = remember {
        val storedPath = prefs.getString("default_path", sharedStorageRoot) ?: sharedStorageRoot
        val storedDirectory = File(storedPath)
        if (storedDirectory.exists() && storedDirectory.isDirectory) storedDirectory.absolutePath else sharedStorageRoot
    }
    var currentPath by remember { mutableStateOf(initial) }
    var pathInput by remember { mutableStateOf(initial) }
    var refresh by remember { mutableIntStateOf(0) }
    var directoryReadError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var advancedSearch by remember { mutableStateOf(false) }
    var searchExtension by remember { mutableStateOf("") }
    var searchType by remember { mutableStateOf("Tous") }
    var minSizeText by remember { mutableStateOf("") }
    var maxSizeText by remember { mutableStateOf("") }
    var searchDate by remember { mutableStateOf("Toutes les dates") }
    var searchKind by remember { mutableStateOf("Tout") }
    var searchSubfolders by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<File>>(emptyList()) }
    var searchingFiles by remember { mutableStateOf(false) }
    var recentPaths by remember { mutableStateOf(prefs.getStringSet("recent_paths", emptySet())?.toList() ?: emptyList()) }
    var bulkRenamePrefix by remember { mutableStateOf("") }
    var bulkRenameSuffix by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(prefs.getBoolean("show_hidden", false)) }
    var layout by remember { mutableStateOf(if (prefs.getString("layout", "list") == "grid") BrowserLayout.GRID else BrowserLayout.LIST) }
    var sort by remember { mutableStateOf(FileSort.valueOf(prefs.getString("sort", "NAME") ?: "NAME")) }
    var ascending by remember { mutableStateOf(prefs.getBoolean("ascending", true)) }
    val selected = remember { mutableStateListOf<String>() }
    var clipboardPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var clipboardMove by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toList() ?: emptyList()) }
    var defaultPaths by remember { mutableStateOf(prefs.getStringSet("default_paths", setOf(initial))?.toList()?.filter { File(it).isDirectory } ?: listOf(initial)) }
    var startupPath by remember { mutableStateOf(prefs.getString("default_path", initial) ?: initial) }
    var compactRows by remember { mutableStateOf(prefs.getBoolean("compact_rows", false)) }
    var message by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf("") }
    var settingsSection by remember { mutableStateOf("Affichage") }
    var nameInput by remember { mutableStateOf("") }
    var createMenuExpanded by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(prefs.getBoolean("confirm_cancel", true)) }
    var confirmReplace by remember { mutableStateOf(prefs.getBoolean("confirm_replace", true)) }
    var interactivePaste by remember { mutableStateOf(prefs.getBoolean("interactive_paste", true)) }
    var deleteAfterZip by remember { mutableStateOf(prefs.getBoolean("delete_after_zip", false)) }
    var chosenArchiveFormat by remember { mutableStateOf(ArchiveFormat.ZIP) }
    var archivePassword by remember { mutableStateOf("") }
    var archiveFormatMenuExpanded by remember { mutableStateOf(false) }
    var openBehavior by remember { mutableStateOf(prefs.getString("open_behavior", "menu") ?: "menu") }
    var archiveDestination by remember { mutableStateOf(prefs.getString("archive_destination", "current") ?: "current") }
    var rememberLastFolder by remember { mutableStateOf(prefs.getBoolean("remember_last_folder", true)) }
    var showFileSizes by remember { mutableStateOf(prefs.getBoolean("show_sizes", true)) }
    var showDates by remember { mutableStateOf(prefs.getBoolean("show_dates", true)) }
    var activeFile by remember { mutableStateOf<File?>(null) }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var editorFile by remember { mutableStateOf<File?>(null) }
    var editorText by remember { mutableStateOf("") }
    var editorDirty by remember { mutableStateOf(false) }
    var propertiesFile by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var canCancelOperation by remember { mutableStateOf(false) }
    var operationProgress by remember { mutableFloatStateOf(0f) }
    var operationLabel by remember { mutableStateOf("") }
    val cancelOperation = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var trashItems by remember { mutableStateOf<List<TrashEntry>>(emptyList()) }
    var duplicateGroups by remember { mutableStateOf<List<List<File>>>(emptyList()) }
    var storageSummary by remember { mutableStateOf<StorageSummary?>(null) }
    // Accès direct : Android 11+ via MANAGE_EXTERNAL_STORAGE ; Android 10
    // via la variante Legacy (target 29 + requestLegacyExternalStorage).
    // La variante Play reste sur SAF pour Android 10.
    fun hasStorageReadAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= 30 -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT == 29 && BuildConfig.LEGACY_STORAGE -> {
            Environment.isExternalStorageLegacy() &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        Build.VERSION.SDK_INT == 29 -> false
        else -> ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    val allFilesGranted = remember { mutableStateOf(hasStorageReadAccess()) }

    val allFilesSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        allFilesGranted.value = hasStorageReadAccess()
        message = if (allFilesGranted.value) "Accès aux fichiers activé" else "Accès étendu non accordé. Vérifie aussi les permissions de stockage."
        if (allFilesGranted.value) refresh++
    }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        allFilesGranted.value = hasStorageReadAccess()
        if (allFilesGranted.value) {
            message = "Accès au stockage partagé activé"
            refresh++
        } else {
            message = "Permission de lecture refusée : les dossiers ne peuvent pas être listés."
        }
    }

    fun refreshNow() { refresh++; selected.clear() }
    fun rememberRecent(file: File) {
        if (!file.exists() || !file.isFile) return
        recentPaths = (listOf(file.absolutePath) + recentPaths.filter { it != file.absolutePath && File(it).exists() }).take(40)
        prefs.edit().putStringSet("recent_paths", recentPaths.toSet()).apply()
    }
    fun go(path: String) {
        val target = runCatching { File(path).canonicalFile }.getOrNull()
        if (target == null || !target.exists() || !target.isDirectory) {
            message = "Chemin inaccessible ou dossier inexistant : $path"
            return
        }
        currentPath = target.absolutePath
        pathInput = target.absolutePath
        message = null
        if (rememberLastFolder) prefs.edit().putString("default_path", target.absolutePath).apply()
        query = ""
        refreshNow()
    }
    fun requestExtendedStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
            runCatching { allFilesSettings.launch(intent) }.onFailure {
                runCatching { allFilesSettings.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                    .onFailure { message = "Impossible d'ouvrir les paramètres d'accès étendu Android." }
            }
        } else {
            legacyPermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }
    fun requestStorage() {
        when {
            Build.VERSION.SDK_INT >= 30 -> {
                // API 30+ : demander directement l'accès spécial, pas les anciennes permissions
                // READ/WRITE qui ne sont plus le mécanisme d'accès général.
                if (!Environment.isExternalStorageManager()) requestExtendedStorageAccess()
                else { allFilesGranted.value = true; refreshNow() }
            }
            Build.VERSION.SDK_INT == 29 && !BuildConfig.LEGACY_STORAGE -> {
                // La variante Play utilise le SAF sur Android 10.
                message = "Accès par dossier requis sur cette variante Android."
            }
            else -> {
                if (!hasStorageReadAccess()) legacyPermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                else { allFilesGranted.value = true; refreshNow() }
            }
        }
    }

    LaunchedEffect(Build.VERSION.SDK_INT, BuildConfig.LEGACY_STORAGE) {
        if (!allFilesGranted.value) requestStorage()
    }

    val currentDir = remember(currentPath, refresh) { File(currentPath) }
    LaunchedEffect(currentPath, refresh, showHidden, query, searchExtension, searchType, minSizeText, maxSizeText, searchDate, searchKind, searchSubfolders, sort, ascending, allFilesGranted.value) {
        searchingFiles = true
        directoryReadError = null
        val rootPath = currentPath
        val now = System.currentTimeMillis()
        directoryReadError = withContext(Dispatchers.IO) {
            val directory = File(rootPath)
            try {
                when {
                    !directory.exists() -> "Ce chemin n'existe pas ou n'est plus monté : $rootPath"
                    !directory.isDirectory -> "Le chemin indiqué n'est pas un dossier : $rootPath"
                    !directory.canRead() -> "Lecture refusée par Android pour : $rootPath"
                    directory.listFiles() == null -> "Android n'a pas autorisé la lecture de ce dossier : $rootPath"
                    else -> null
                }
            } catch (error: SecurityException) {
                "Accès refusé par Android : ${error.message ?: rootPath}"
            } catch (error: Exception) {
                "Impossible de lire le dossier : ${error.message ?: rootPath}"
            }
        }
        val dateCutoff = when (searchDate) {
            "24 dernières heures" -> now - 24L * 60 * 60 * 1000
            "7 derniers jours" -> now - 7L * 24 * 60 * 60 * 1000
            "30 derniers jours" -> now - 30L * 24 * 60 * 60 * 1000
            else -> 0L
        }
        val minBytes = minSizeText.toLongOrNull()?.let { it * 1024L }
        val maxBytes = maxSizeText.toLongOrNull()?.let { it * 1024L }
        val results = withContext(Dispatchers.IO) {
            val found = ArrayList<File>()
            val stack = ArrayDeque<File>()
            stack.add(File(rootPath))
            var visited = 0
            while (stack.isNotEmpty() && visited < 10000) {
                val dir = stack.removeLast()
                val children = runCatching { dir.listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())
                for (f in children) {
                    visited++
                    if (visited >= 10000) break
                    if (!showHidden && f.name.startsWith(".")) continue
                    if (f.isDirectory && searchSubfolders) stack.add(f)
                    val kindOk = when (searchKind) { "Fichiers" -> f.isFile; "Dossiers" -> f.isDirectory; else -> true }
                    val typeOk = searchType == "Tous" || when (searchType) {
                        "Images" -> f.extension.lowercase() in listOf("jpg","jpeg","png","gif","webp","bmp","heic")
                        "Vidéos" -> f.extension.lowercase() in listOf("mp4","mkv","mov","webm","3gp")
                        "Audio" -> f.extension.lowercase() in listOf("mp3","wav","m4a","ogg","flac")
                        "Documents" -> f.extension.lowercase() in listOf("pdf","doc","docx","txt","rtf","odt","xls","xlsx","ppt","pptx")
                        "Archives" -> f.extension.lowercase() in listOf("zip","rar","7z","tar","gz")
                        else -> true
                    }
                    val matches = (query.isBlank() || f.name.contains(query.trim(), ignoreCase = true)) &&
                        (searchExtension.isBlank() || f.extension.equals(searchExtension.trim().removePrefix("."), true)) &&
                        typeOk && kindOk && (dateCutoff == 0L || f.lastModified() >= dateCutoff) &&
                        (minBytes == null || f.isDirectory || f.length() >= minBytes) &&
                        (maxBytes == null || f.isDirectory || f.length() <= maxBytes)
                    if (matches) found.add(f)
                }
            }
            found.sortedWith { first, second ->
                if (first.isDirectory != second.isDirectory) {
                    if (first.isDirectory) -1 else 1
                } else {
                    val compared = when (sort) {
                        FileSort.NAME -> first.name.lowercase(Locale.ROOT).compareTo(second.name.lowercase(Locale.ROOT))
                        FileSort.SIZE -> if (first.isDirectory) first.name.compareTo(second.name, ignoreCase = true) else first.length().compareTo(second.length())
                        FileSort.DATE -> first.lastModified().compareTo(second.lastModified())
                        FileSort.TYPE -> first.extension.lowercase(Locale.ROOT).compareTo(second.extension.lowercase(Locale.ROOT)).takeIf { it != 0 }
                            ?: first.name.lowercase(Locale.ROOT).compareTo(second.name.lowercase(Locale.ROOT))
                    }
                    if (ascending) compared else -compared
                }
            }
        }
        searchResults = results
        searchingFiles = false
    }
    val files = searchResults
    val selectedLocalFiles = collapseLocalSelection(selected.map { File(it) })
    LaunchedEffect(currentPath, query, searchExtension, searchType, searchKind, searchDate, minSizeText, maxSizeText, searchSubfolders, showHidden) {
        selected.clear()
    }

    BackHandler(enabled = busy || dialog.isNotEmpty() || previewFile != null || editorFile != null || propertiesFile != null || selected.isNotEmpty() || currentPath != sharedStorageRoot) {
        when {
            busy -> message = "Opération en cours : attends sa fin ou utilise Annuler si cette opération le permet."
            dialog.isNotEmpty() -> dialog = ""
            previewFile != null -> previewFile = null
            editorFile != null -> { if (editorDirty) dialog = "unsaved" else editorFile = null }
            propertiesFile != null -> propertiesFile = null
            selected.isNotEmpty() -> selected.clear()
            else -> {
                val parent = currentDir.parentFile
                if (parent != null) go(parent.absolutePath)
            }
        }
    }

    when (dialog) {
        "recent" -> {
            RecentFilesPage(
                paths = recentPaths,
                onOpen = { file ->
                    dialog = ""
                    file.parentFile?.let { go(it.absolutePath) }
                    rememberRecent(file)
                    when (openBehavior) {
                        "external" -> openWithExternal(context, file) { message = it }
                        "menu" -> { activeFile = file; dialog = "actions" }
                        else -> openFile(file, onPreview = { previewFile = file }, onEdit = {
                            if (file.length() > 5L * 1024L * 1024L) message = "Fichier trop volumineux pour l’éditeur intégré (limite actuelle : 5 Mio)."
                            else { editorFile = file; editorText = runCatching { file.readText(Charsets.UTF_8) }.getOrDefault(""); editorDirty = false }
                        })
                    }
                },
                onInfo = { file -> dialog = ""; propertiesFile = file },
                onClear = { recentPaths = emptyList(); prefs.edit().remove("recent_paths").apply() },
                onBack = { dialog = "" }
            )
            return
        }
        "favorites" -> {
            FavoritesPage(
                paths = favorites,
                onOpen = { path -> dialog = ""; go(path) },
                onRemove = { path -> favorites = favorites - path; prefs.edit().putStringSet("favorites", favorites.toSet()).apply() },
                onBack = { dialog = "" }
            )
            return
        }
        "trash" -> {
            TrashPage(
                entries = trashItems,
                onRestore = { entry ->
                    val restored = restoreFromTrash(entry)
                    message = if (restored) "Élément restauré" else "Restauration impossible : emplacement déjà occupé"
                    trashItems = loadTrash(context)
                    refreshNow()
                },
                onPurge = {
                    val failed = mutableListOf<TrashEntry>()
                    var count = 0
                    trashItems.forEach { entry ->
                        val deleted = runCatching {
                            val removed = if (entry.file.isDirectory) entry.file.deleteRecursively() else entry.file.delete()
                            if (removed) File(entry.file.parentFile, entry.file.name + ".origin").delete()
                            removed
                        }.getOrDefault(false)
                        if (deleted) count++ else failed.add(entry)
                    }
                    trashItems = failed
                    message = if (failed.isEmpty()) "$count élément(s) supprimé(s) définitivement" else "$count supprimé(s), ${failed.size} impossible(s) à supprimer"
                },
                onBack = { dialog = "" }
            )
            return
        }
        "duplicates" -> {
            DuplicatesPage(groups = duplicateGroups, onBack = { dialog = "" })
            return
        }
        "storage" -> {
            storageSummary?.let { summary -> StoragePage(summary, onBack = { dialog = "" }) }
            return
        }
        "permissions" -> {
            PermissionsPage(granted = allFilesGranted.value, onAuthorize = { dialog = ""; if (!allFilesGranted.value) requestStorage() }, onBack = { dialog = "" })
            return
        }
    }

    if (dialog == "tools") {
        ZernexToolsPage(
            onBack = { dialog = "" },
            onStorage = { storageSummary = readStorageSummary(Environment.getExternalStorageDirectory()); dialog = "storage" },
            onTrash = { trashItems = loadTrash(context); dialog = "trash" },
            onDuplicates = {
                busy = true
                operationLabel = "Recherche des doublons…"
                Thread {
                    val groups = findDuplicateGroups(Environment.getExternalStorageDirectory())
                    Handler(Looper.getMainLooper()).post {
                        duplicateGroups = groups
                        busy = false
                        dialog = "duplicates"
                    }
                }.start()
            },
            onPermissions = { dialog = "permissions" },
            onSettings = { dialog = "settings" },
            onChooseSaf = { dialog = ""; onChooseSaf() }
        )
        return
    }

    if (previewFile != null) {
        FilePreviewScreen(previewFile!!, onClose = { previewFile = null })
        return
    }
    if (editorFile != null) {
        FileEditorScreen(editorFile!!, editorText, onText = { editorText = it; editorDirty = true }, onSave = { content ->
            runCatching { saveTextSafely(editorFile!!, content) }.fold(
                onSuccess = { editorDirty = false; message = "Fichier enregistré"; true },
                onFailure = { message = "Enregistrement impossible : ${it.message}"; false }
            )
        }, onClose = { if (editorDirty) dialog = "unsaved" else editorFile = null })
        if (dialog == "unsaved") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Modifications non enregistrées") }, text = { Text("Fermer sans enregistrer les changements ?") }, confirmButton = { TextButton(onClick = { dialog = ""; editorFile = null; editorDirty = false }) { Text("Fermer sans enregistrer") } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Continuer l'édition") } })
        return
    }

    propertiesFile?.let { file ->
        FileDetailsPage(file = file, onBack = { propertiesFile = null })
        return
    }

    if (dialog == "settings") {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { dialog = "" }) { Icon(Icons.Default.ArrowBack, "Retour") }
                Column(Modifier.weight(1f)) {
                    Text("Paramètres", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Personnaliser ZERNEX Files", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                listOf("Affichage", "Ouverture", "Archives", "Actions", "Dossiers").forEach { section ->
                    FilterChip(
                        selected = settingsSection == section,
                        onClick = { settingsSection = section },
                        label = { Text(section) },
                        leadingIcon = if (settingsSection == section) {{ Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }} else null
                    )
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (settingsSection) {
                    "Affichage" -> {
                        Text("Affichage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Choisis comment les éléments apparaissent dans l’explorateur.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SettingsGroup {
                            SettingsSwitchRow("Vue en grille", "Afficher les éléments sous forme de cartes", layout == BrowserLayout.GRID) { enabled -> layout = if (enabled) BrowserLayout.GRID else BrowserLayout.LIST; prefs.edit().putString("layout", if (enabled) "grid" else "list").apply() }
                            SettingsSwitchRow("Fichiers cachés", "Afficher les éléments dont le nom commence par un point", showHidden) { enabled -> showHidden = enabled; prefs.edit().putBoolean("show_hidden", enabled).apply(); refresh++ }
                            SettingsSwitchRow("Affichage compact", "Réduire l’espace entre les éléments", compactRows) { enabled -> compactRows = enabled; prefs.edit().putBoolean("compact_rows", enabled).apply() }
                            SettingsSwitchRow("Afficher la taille", "Voir le poids de chaque fichier", showFileSizes) { enabled -> showFileSizes = enabled; prefs.edit().putBoolean("show_sizes", enabled).apply() }
                            SettingsSwitchRow("Afficher la date", "Voir la dernière modification", showDates) { enabled -> showDates = enabled; prefs.edit().putBoolean("show_dates", enabled).apply() }
                        }
                        SettingsGroup {
                            Text("Trier les fichiers par", fontWeight = FontWeight.SemiBold)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(FileSort.NAME to "Nom", FileSort.DATE to "Date", FileSort.SIZE to "Taille", FileSort.TYPE to "Type").forEach { (value, label) ->
                                    FilterChip(selected = sort == value, onClick = { sort = value; prefs.edit().putString("sort", value.name).apply() }, label = { Text(label) })
                                }
                            }
                            SettingsSwitchRow("Ordre croissant", "Désactive pour afficher du plus récent / grand au plus ancien / petit", ascending) { enabled -> ascending = enabled; prefs.edit().putBoolean("ascending", enabled).apply() }
                        }
                    }
                    "Ouverture" -> {
                        Text("Ouverture", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Définis l’action effectuée lorsque tu touches un fichier.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SettingsGroup {
                            Text("Comportement à l’ouverture", fontWeight = FontWeight.SemiBold)
                            listOf("menu" to "Me demander", "preview" to "Ouvrir dans ZERNEX", "external" to "Utiliser une autre application").forEach { (value, label) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = openBehavior == value, onClick = { openBehavior = value; prefs.edit().putString("open_behavior", value).apply() })
                                    Text(label, Modifier.weight(1f))
                                }
                            }
                            SettingsSwitchRow("Retrouver le dernier dossier", "Revenir au même emplacement à la prochaine ouverture", rememberLastFolder) { enabled -> rememberLastFolder = enabled; prefs.edit().putBoolean("remember_last_folder", enabled).apply() }
                        }
                    }
                    "Archives" -> {
                        Text("Archives", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Configure la création des fichiers ZIP.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SettingsGroup {
                            Text("Destination des nouvelles archives", fontWeight = FontWeight.SemiBold)
                            listOf("current" to "Dans le dossier actuel", "archives" to "Dans un sous-dossier « Archives »").forEach { (value, label) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = archiveDestination == value, onClick = { archiveDestination = value; prefs.edit().putString("archive_destination", value).apply() })
                                    Text(label, Modifier.weight(1f))
                                }
                            }
                            SettingsSwitchRow("Garder les fichiers d’origine", "Ne pas supprimer les éléments après compression", !deleteAfterZip) { enabled -> deleteAfterZip = !enabled; prefs.edit().putBoolean("delete_after_zip", deleteAfterZip).apply() }
                        }
                        SettingsHint("Conseil : conserve les fichiers d’origine pour éviter toute perte accidentelle.")
                    }
                    "Actions" -> {
                        Text("Actions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Ajuste les confirmations affichées pendant les opérations.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SettingsGroup {
                            SettingsSwitchRow("Confirmer avant annulation", "Demander avant d’interrompre une opération", confirmCancel) { enabled -> confirmCancel = enabled; prefs.edit().putBoolean("confirm_cancel", enabled).apply() }
                            SettingsSwitchRow("Confirmer le remplacement", "Demander avant d’écraser un fichier existant", confirmReplace) { enabled -> confirmReplace = enabled; prefs.edit().putBoolean("confirm_replace", enabled).apply() }
                            SettingsSwitchRow("Collage avec confirmation", "Vérifier la destination avant de copier ou déplacer", interactivePaste) { enabled -> interactivePaste = enabled; prefs.edit().putBoolean("interactive_paste", enabled).apply() }
                        }
                    }
                    "Dossiers" -> {
                        Text("Dossiers et emplacements", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("La navigation par chemin et les emplacements enregistrés sont regroupés ici pour libérer l’écran des fichiers.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SettingsGroup {
                            Text("Chemin actuel", fontWeight = FontWeight.SemiBold)
                            Text(currentPath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = pathInput,
                                onValueChange = { pathInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Saisir un chemin") },
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                leadingIcon = { Icon(Icons.Default.FolderOpen, null) }
                            )
                            Button(onClick = { go(pathInput); if (message == null || !message!!.startsWith("Chemin inaccessible")) dialog = "" }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Ouvrir cet emplacement")
                            }
                            OutlinedButton(onClick = { go("/storage/emulated/0"); dialog = "" }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Home, null); Spacer(Modifier.width(8.dp)); Text("Revenir au stockage interne")
                            }
                        }
                        SettingsGroup {
                            Text("Emplacements favoris", fontWeight = FontWeight.SemiBold)
                            Text("Choisis les dossiers à retrouver rapidement et celui qui s’ouvre au démarrage.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (defaultPaths.isEmpty()) Text("Aucun emplacement enregistré.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            defaultPaths.forEach { path ->
                                val isStartup = path == startupPath
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(File(path).name.ifBlank { "Stockage" }, maxLines = 1, fontWeight = FontWeight.SemiBold)
                                        Text(path, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { go(path); dialog = "" }) { Icon(Icons.Default.FolderOpen, "Ouvrir") }
                                    if (!isStartup) IconButton(onClick = { startupPath = path; prefs.edit().putString("default_path", path).apply() }) { Icon(Icons.Default.Home, "Définir au démarrage") }
                                    else Icon(Icons.Default.CheckCircle, "Dossier de démarrage", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    if (path != startupPath && defaultPaths.size > 1) IconButton(onClick = { defaultPaths = defaultPaths - path; prefs.edit().putStringSet("default_paths", defaultPaths.toSet()).apply() }) { Icon(Icons.Default.Close, "Retirer") }
                                }
                                if (path != defaultPaths.last()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            OutlinedButton(onClick = { if (currentPath !in defaultPaths) { defaultPaths = (defaultPaths + currentPath).distinct(); prefs.edit().putStringSet("default_paths", defaultPaths.toSet()).apply() } else message = "Ce dossier est déjà enregistré" }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.BookmarkAdd, null); Spacer(Modifier.width(8.dp)); Text("Ajouter le dossier actuel aux favoris")
                            }
                            Text("Dossier de démarrage : ${File(startupPath).name.ifBlank { startupPath }}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        SettingsGroup {
                            Text("Accès au stockage", fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (allFilesGranted.value) Icons.Default.CheckCircle else Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(9.dp))
                                Text(if (allFilesGranted.value) "Accès au stockage autorisé" else "Autorisation de stockage requise", Modifier.weight(1f))
                            }
                            Text("Cette version APK active le mode de compatibilité de stockage pour les appareils Android 10 et demande l'accès étendu sur les versions récentes. Les données privées des applications, /data et certaines zones système restent protégées sans privilèges root.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!allFilesGranted.value) Button(onClick = { requestStorage() }, modifier = Modifier.fillMaxWidth()) { Text("Accorder l'accès aux fichiers") }
                            if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) OutlinedButton(onClick = { requestExtendedStorageAccess() }, modifier = Modifier.fillMaxWidth()) { Text("Ouvrir l'autorisation spéciale « Tous les fichiers »") }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    showHidden = false; layout = BrowserLayout.LIST; compactRows = false; showFileSizes = true; showDates = true
                    sort = FileSort.NAME; ascending = true; openBehavior = "menu"; rememberLastFolder = true
                    archiveDestination = "current"; deleteAfterZip = false; confirmCancel = true; confirmReplace = true; interactivePaste = true
                    prefs.edit().putBoolean("show_hidden", false).putString("layout", "list").putBoolean("compact_rows", false)
                        .putBoolean("show_sizes", true).putBoolean("show_dates", true).putString("sort", "NAME").putBoolean("ascending", true)
                        .putString("open_behavior", "menu").putBoolean("remember_last_folder", true).putString("archive_destination", "current")
                        .putBoolean("delete_after_zip", false).putBoolean("confirm_cancel", true).putBoolean("confirm_replace", true)
                        .putBoolean("interactive_paste", true).apply()
                    message = "Réglages réinitialisés"; refresh++
                }) { Text("Réinitialiser") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { dialog = ""; refresh++ }) { Text("Terminé") }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
      Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 42.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(enabled = !busy && currentPath != sharedStorageRoot, onClick = {
                val rootCanonical = runCatching { File(sharedStorageRoot).canonicalPath }.getOrDefault(sharedStorageRoot)
                val parent = currentDir.parentFile
                val parentCanonical = parent?.let { runCatching { it.canonicalPath }.getOrNull() }
                go(if (parentCanonical != null && (parentCanonical == rootCanonical || parentCanonical.startsWith(rootCanonical.trimEnd(File.separatorChar) + File.separator))) parentCanonical else rootCanonical)
            }) { Icon(Icons.Default.ArrowUpward, contentDescription = "Dossier parent ou racine", tint = if (currentPath != sharedStorageRoot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.width(2.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ZernexBrandMark()
                    Text("ZERNEX Files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
                }
                val localPathParts = remember(currentPath, sharedStorageRoot) {
                    runCatching {
                        val relative = File(currentPath).canonicalFile.relativeTo(File(sharedStorageRoot).canonicalFile).path
                        if (relative == "." || relative.isBlank()) emptyList() else relative.split(File.separatorChar).filter { it.isNotBlank() }
                    }.getOrNull()
                }
                if (localPathParts != null) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        Text("Stockage", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { go(sharedStorageRoot) })
                        var crumbPath = sharedStorageRoot
                        localPathParts.forEach { part ->
                            val targetPath = File(crumbPath, part).absolutePath
                            crumbPath = targetPath
                            Text(" / ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(part, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.clickable(enabled = !busy) { go(targetPath) })
                        }
                    }
                } else {
                    Text(currentPath, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            IconButton(enabled = !busy, onClick = { searchOpen = !searchOpen; query = "" }) {
                Icon(Icons.Default.Search, "Rechercher", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(enabled = !busy, onClick = { createMenuExpanded = true }) {
                    Icon(Icons.Default.Add, "Créer", tint = MaterialTheme.colorScheme.primary)
                }
                DropdownMenu(expanded = createMenuExpanded, onDismissRequest = { createMenuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Nouveau dossier") }, leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) }, onClick = { nameInput = ""; dialog = "mkdir"; createMenuExpanded = false })
                    DropdownMenuItem(text = { Text("Nouveau fichier texte") }, leadingIcon = { Icon(Icons.Default.NoteAdd, null) }, onClick = { nameInput = ""; dialog = "newtxt"; createMenuExpanded = false })
                }
            }
            IconButton(enabled = !busy, onClick = { dialog = "settings" }) {
                Icon(Icons.Default.Settings, "Paramètres", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant, shape = RoundedCornerShape(50)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Text("${files.size} éléments", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(enabled = !busy, onClick = { layout = if (layout == BrowserLayout.LIST) BrowserLayout.GRID else BrowserLayout.LIST; prefs.edit().putString("layout", if (layout == BrowserLayout.GRID) "list" else "grid").apply() }) { Icon(if (layout == BrowserLayout.LIST) Icons.Default.GridView else Icons.Default.ViewList, "Changer l'affichage") }
            var sortMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(enabled = !busy, onClick = { sortMenu = true }) { Icon(Icons.Default.Sort, "Trier") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    FileSort.entries.forEach { item -> DropdownMenuItem(enabled = !busy, text = { Text(when(item) { FileSort.NAME -> "Nom"; FileSort.SIZE -> "Taille"; FileSort.DATE -> "Date"; FileSort.TYPE -> "Type" }) }, onClick = { sort = item; prefs.edit().putString("sort", item.name).apply(); sortMenu = false }) }
                    DropdownMenuItem(enabled = !busy, text = { Text(if (ascending) "Ordre décroissant" else "Ordre croissant") }, onClick = { ascending = !ascending; sortMenu = false })
                }
            }
            var more by remember { mutableStateOf(false) }
            Box {
                IconButton(enabled = !busy, onClick = { more = true }) { Icon(Icons.Default.MoreVert, "Plus") }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(enabled = !busy, text = { Text(if (showHidden) "Masquer fichiers cachés" else "Afficher fichiers cachés") }, leadingIcon = { Icon(Icons.Default.Visibility, null) }, onClick = { showHidden = !showHidden; prefs.edit().putBoolean("show_hidden", showHidden).apply(); more = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Fichiers récents") }, leadingIcon = { Icon(Icons.Default.History, null) }, onClick = { dialog = "recent"; more = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Recherche avancée") }, leadingIcon = { Icon(Icons.Default.ManageSearch, null) }, onClick = { advancedSearch = true; searchOpen = true; more = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Nouveau dossier") }, leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) }, onClick = { nameInput = ""; dialog = "mkdir"; more = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Ajouter aux favoris") }, leadingIcon = { Icon(Icons.Default.Star, null) }, onClick = { favorites = (favorites + currentPath).distinct(); prefs.edit().putStringSet("favorites", favorites.toSet()).apply(); message = "Favori ajouté"; more = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Favoris (${favorites.size})") }, leadingIcon = { Icon(Icons.Default.Bookmarks, null) }, onClick = { dialog = "favorites"; more = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Ajouter aux chemins favoris") }, leadingIcon = { Icon(Icons.Default.BookmarkAdd, null) }, onClick = { defaultPaths = (defaultPaths + currentPath).distinct(); prefs.edit().putStringSet("default_paths", defaultPaths.toSet()).apply(); message = "Chemin ajouté aux paramètres"; more = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Autorisation stockage étendu") }, leadingIcon = { Icon(Icons.Default.FolderOpen, null) }, onClick = { requestStorage(); more = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Choisir un dossier avec Android (SAF)") }, leadingIcon = { Icon(Icons.Default.FolderSpecial, null) }, onClick = { onChooseSaf(); more = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Corbeille") }, leadingIcon = { Icon(Icons.Default.DeleteSweep, null) }, onClick = { trashItems = loadTrash(context); dialog = "trash"; more = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Fichiers en double") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { busy = true; operationLabel = "Recherche des doublons…"; Thread { val groups = findDuplicateGroups(Environment.getExternalStorageDirectory()); Handler(Looper.getMainLooper()).post { duplicateGroups = groups; busy = false; dialog = "duplicates" } }.start(); more = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Espace de stockage") }, leadingIcon = { Icon(Icons.Default.Storage, null) }, onClick = { storageSummary = readStorageSummary(Environment.getExternalStorageDirectory()); dialog = "storage"; more = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Paramètres") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { dialog = "settings"; more = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Autorisations") }, leadingIcon = { Icon(Icons.Default.Security, null) }, onClick = { dialog = "permissions"; more = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Propriétés du dossier") }, leadingIcon = { Icon(Icons.Default.Info, null) }, onClick = { propertiesFile = currentDir; more = false })
                }
            }
        }
        ZernexCategoryRail(selected = searchType, onSelected = { category ->
            searchType = category
            advancedSearch = false
            query = ""
        })
        if (searchOpen) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Nom de fichier ou de dossier…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, null) } }); if (searchingFiles) LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (advancedSearch) Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Filtres de recherche", Modifier.weight(1f), fontWeight = FontWeight.SemiBold); IconButton(onClick = { advancedSearch = false }) { Icon(Icons.Default.Close, "Fermer") } }
                OutlinedTextField(searchExtension, { searchExtension = it.trimStart('.') }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Type de fichier (ex. pdf, jpg, apk)") }, leadingIcon = { Icon(Icons.Default.InsertDriveFile, null) })
                Text("Catégorie", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Tous", "Images", "Vidéos", "Audio", "Documents", "Archives").forEach { FilterChip(selected = searchType == it, onClick = { searchType = it }, label = { Text(it) }) } }
                Text("Éléments à afficher", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Tout", "Fichiers", "Dossiers").forEach { FilterChip(selected = searchKind == it, onClick = { searchKind = it }, label = { Text(it) }) } }
                Text("Date de modification", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Toutes les dates", "24 dernières heures", "7 derniers jours", "30 derniers jours").forEach { FilterChip(selected = searchDate == it, onClick = { searchDate = it }, label = { Text(it) }) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(minSizeText, { minSizeText = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Taille min. Ko") }, singleLine = true); OutlinedTextField(maxSizeText, { maxSizeText = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Taille max. Ko") }, singleLine = true) }
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Inclure les sous-dossiers", fontWeight = FontWeight.Medium); Text("Chercher aussi à l'intérieur des dossiers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked = searchSubfolders, onCheckedChange = { searchSubfolders = it }) }
                if (searchingFiles) LinearProgressIndicator(Modifier.fillMaxWidth()) else Text("${files.size} résultat(s)${if (searchSubfolders) " · sous-dossiers inclus" else " · dossier actuel"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { searchExtension = ""; searchType = "Tous"; searchKind = "Tout"; searchDate = "Toutes les dates"; minSizeText = ""; maxSizeText = ""; searchSubfolders = false; query = "" }) { Text("Tout effacer") }
                    TextButton(onClick = { advancedSearch = false }) { Text("Terminé") }
                }
            }
        }
        message?.let { msg -> Row(verticalAlignment = Alignment.CenterVertically) { Text(msg, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall); IconButton(onClick = { message = null }) { Icon(Icons.Default.Close, null) } } }
        if (clipboardPaths.isNotEmpty()) {
            Text("Presse-papiers actif : colle les éléments ou efface-le avant de renommer, supprimer ou recompresser des fichiers.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${clipboardPaths.size} élément(s) dans le presse-papiers", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !busy, onClick = {
                    val paths = clipboardPaths.toList()
                    val move = clipboardMove
                    busy = true
                    canCancelOperation = true
                    operationProgress = 0f
                    operationLabel = if (move) "Déplacement…" else "Copie…"
                    cancelOperation.set(false)
                    Thread {
                        val result = transferLocalFiles(paths, currentDir, move, cancelOperation) { done, total ->
                            Handler(Looper.getMainLooper()).post {
                                operationProgress = done.toFloat() / total.coerceAtLeast(1)
                                operationLabel = "Élément $done sur $total"
                            }
                        }
                        Handler(Looper.getMainLooper()).post {
                            busy = false
                            canCancelOperation = false
                            clipboardPaths = paths.filterNot { it in result.completedPaths }
                            message = buildString {
                                append("${result.completedPaths.size} élément(s) traité(s)")
                                if (result.skipped > 0) append(" • ${result.skipped} déjà à cet emplacement")
                                if (result.warnings.isNotEmpty()) append(" • ${result.warnings.take(2).joinToString(" ; ")}")
                                if (result.cancelled) append(" • opération interrompue")
                                if (clipboardPaths.isNotEmpty()) append(" • ${clipboardPaths.size} restant(s) dans le presse-papiers")
                            }
                            refreshNow()
                        }
                    }.start()
                }) { Text("Coller ici") }
                TextButton(enabled = !busy, onClick = { clipboardPaths = emptyList() }) { Text("Effacer") }
            }
        }
        if (busy) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(operationLabel.ifBlank { "Opération en cours…" }, style = MaterialTheme.typography.labelSmall)
            if (canCancelOperation) LinearProgressIndicator(progress = { operationProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
            if (canCancelOperation) TextButton(onClick = { cancelOperation.set(true) }) { Icon(Icons.Default.Close, null); Spacer(Modifier.width(5.dp)); Text("Annuler après le fichier en cours") }
            else Text("Ne ferme pas l'application pendant cette opération.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(24.dp)) {
                    if (searchingFiles) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text("Lecture des dossiers et fichiers…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (!allFilesGranted.value) {
                        Icon(Icons.Default.FolderOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Autorisation de stockage requise", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        Text("Autorise l’accès aux fichiers pour afficher le contenu réel de ton appareil.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { requestStorage() }) { Icon(Icons.Default.Lock, null); Spacer(Modifier.width(7.dp)); Text("Autoriser l’accès") }
                    } else {
                        Icon(Icons.Default.FolderOff, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(directoryReadError ?: "Ce dossier est vide", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        Text(currentPath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (directoryReadError != null && Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                            Button(onClick = { requestStorage() }) { Icon(Icons.Default.Lock, null); Spacer(Modifier.width(6.dp)); Text("Vérifier les autorisations") }
                        }
                        TextButton(onClick = { refreshNow() }) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Actualiser") }
                    }
                }
            }
        } else if (layout == BrowserLayout.LIST) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = if (selected.isNotEmpty()) 112.dp else 88.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(files, key = { it.absolutePath }) { file ->
                    val toggleSelection: () -> Unit = { if (!busy) { if (selected.contains(file.absolutePath)) selected.remove(file.absolutePath) else selected.add(file.absolutePath) } }
                    FileListRow(
                        file = file, isSelected = selected.contains(file.absolutePath), compact = compactRows,
                        showSize = showFileSizes, showDate = showDates, enabled = !busy,
                        onClick = { if (busy) Unit else if (selected.isNotEmpty()) toggleSelection() else if (file.isDirectory) go(file.absolutePath) else {
                            rememberRecent(file)
                            when (openBehavior) {
                                "external" -> openWithExternal(context, file) { message = it }
                                "menu" -> { activeFile = file; dialog = "actions" }
                                else -> openFile(file, onPreview = { previewFile = file }, onEdit = {
                                    if (file.length() > 5L * 1024L * 1024L) message = "Fichier trop volumineux pour l'éditeur intégré (limite actuelle : 5 Mio)."
                                    else { editorFile = file; editorText = runCatching { file.readText(Charsets.UTF_8) }.getOrDefault(""); editorDirty = false }
                                })
                            }
                        } },
                        onLongClick = { toggleSelection() },
                        
                    )
                }
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 112.dp), modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = if (selected.isNotEmpty()) 132.dp else 96.dp)) {
                gridItems(files, key = { it.absolutePath }) { file ->
                    val toggleSelection: () -> Unit = { if (!busy) { if (selected.contains(file.absolutePath)) selected.remove(file.absolutePath) else selected.add(file.absolutePath) } }
                    FileGridCard(
                        file = file, isSelected = selected.contains(file.absolutePath), showSize = showFileSizes,
                        showDate = showDates, enabled = !busy,
                        onClick = { if (busy) Unit else if (selected.isNotEmpty()) toggleSelection() else if (file.isDirectory) go(file.absolutePath) else {
                            rememberRecent(file)
                            when (openBehavior) {
                                "external" -> openWithExternal(context, file) { message = it }
                                "menu" -> { activeFile = file; dialog = "actions" }
                                else -> openFile(file, onPreview = { previewFile = file }, onEdit = {
                                    if (file.length() > 5L * 1024L * 1024L) message = "Fichier trop volumineux pour l'éditeur intégré (limite actuelle : 5 Mio)."
                                    else { editorFile = file; editorText = runCatching { file.readText(Charsets.UTF_8) }.getOrDefault(""); editorDirty = false }
                                })
                            }
                        } },
                        onLongClick = { toggleSelection() },
                        
                    )
                }
            }
        }
      }
      if (selected.isNotEmpty()) {
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${selectedLocalFiles.size} élément(s) sélectionné(s)", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    TextButton(enabled = !busy, onClick = {
                        val visible = files.map { it.absolutePath }
                        if (visible.isNotEmpty() && visible.all { it in selected }) selected.clear()
                        else { selected.clear(); selected.addAll(visible) }
                    }) { Text(if (files.isNotEmpty() && files.all { it.absolutePath in selected }) "Tout désélectionner" else "Tout sélectionner") }
                    TextButton(enabled = !busy, onClick = { selected.clear() }) { Text("Fermer") }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(enabled = !busy && clipboardPaths.isEmpty() && selectedLocalFiles.isNotEmpty(), onClick = {
                        clipboardPaths = selectedLocalFiles.map { it.absolutePath }; clipboardMove = false; selected.clear()
                        message = "${clipboardPaths.size} élément(s) prêt(s) à copier. Ouvre le dossier voulu puis touche Coller."
                    }) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("Copier") }
                    TextButton(enabled = !busy && clipboardPaths.isEmpty() && selectedLocalFiles.isNotEmpty(), onClick = {
                        clipboardPaths = selectedLocalFiles.map { it.absolutePath }; clipboardMove = true; selected.clear()
                        message = "${clipboardPaths.size} élément(s) prêt(s) à déplacer. Ouvre le dossier voulu puis touche Coller."
                    }) { Icon(Icons.Default.ContentCut, null); Spacer(Modifier.width(4.dp)); Text("Couper") }
                    TextButton(enabled = !busy && clipboardPaths.isEmpty() && selectedLocalFiles.isNotEmpty(), onClick = {
                        if (selectedLocalFiles.size == 1) { activeFile = selectedLocalFiles.first(); nameInput = selectedLocalFiles.first().name; dialog = "rename" }
                        else { bulkRenamePrefix = ""; bulkRenameSuffix = ""; dialog = "bulk_rename" }
                    }) { Icon(Icons.Default.DriveFileRenameOutline, null); Spacer(Modifier.width(4.dp)); Text("Renommer") }
                    TextButton(enabled = !busy && clipboardPaths.isEmpty() && selectedLocalFiles.isNotEmpty(), onClick = { dialog = "delete" }) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(4.dp)); Text("Corbeille") }
                    TextButton(enabled = !busy && selectedLocalFiles.isNotEmpty(), onClick = { shareLocalFiles(context, selectedLocalFiles) }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(4.dp)); Text("Partager") }
                    if (selectedLocalFiles.size == 1) TextButton(enabled = !busy, onClick = { propertiesFile = selectedLocalFiles.first() }) { Icon(Icons.Default.Info, null); Spacer(Modifier.width(4.dp)); Text("Détails") }
                    TextButton(enabled = !busy && clipboardPaths.isEmpty() && selectedLocalFiles.isNotEmpty(), onClick = {
                        chosenArchiveFormat = ArchiveFormat.ZIP
                        archivePassword = ""
                        dialog = "create_archive"
                    }) { Icon(Icons.Default.FolderZip, null); Spacer(Modifier.width(4.dp)); Text("Compresser") }
                }
            }
        }
      }
      if (selected.isEmpty()) {
        ZernexBottomNavigation(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding(),
            onFiles = { dialog = "" },
            onRecent = { dialog = "recent" },
            onFavorites = { dialog = "favorites" },
            onTools = { dialog = "tools" }
        )
      }

    }

    if (dialog == "create_archive") AlertDialog(
        onDismissRequest = { dialog = ""; archivePassword = ""; archiveFormatMenuExpanded = false },
        title = { Text("Créer une archive") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${selectedLocalFiles.size} élément(s) seront compressés. Le fichier final sera créé dans le dossier de destination configuré.", style = MaterialTheme.typography.bodySmall)
                Box {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { archiveFormatMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Format : ${chosenArchiveFormat.label}", modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ExpandMore, contentDescription = "Choisir le format")
                    }
                    DropdownMenu(
                        expanded = archiveFormatMenuExpanded,
                        onDismissRequest = { archiveFormatMenuExpanded = false }
                    ) {
                        ArchiveFormat.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label + option.extension) },
                                onClick = {
                                    chosenArchiveFormat = option
                                    archiveFormatMenuExpanded = false
                                    if (option != ArchiveFormat.ZIP) archivePassword = ""
                                }
                            )
                        }
                    }
                }
                if (chosenArchiveFormat == ArchiveFormat.ZIP) {
                    OutlinedTextField(
                        value = archivePassword,
                        onValueChange = { archivePassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Mot de passe ZIP (facultatif)") },
                        supportingText = { Text("Chiffrement AES-256. 8 caractères minimum si utilisé.") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                } else {
                    Text("Ce format est créé sans mot de passe. Le chiffrement est actuellement disponible uniquement pour ZIP.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Les liens symboliques sont refusés. Aucun fichier existant ne sera remplacé.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && selectedLocalFiles.isNotEmpty() &&
                    (archivePassword.isBlank() || (chosenArchiveFormat == ArchiveFormat.ZIP && archivePassword.length >= 8)),
                onClick = {
                    val sources = selectedLocalFiles.toList()
                    val format = chosenArchiveFormat
                    val password = archivePassword.takeIf { it.isNotBlank() }
                    dialog = ""
                    archivePassword = ""
                    busy = true
                    operationLabel = "Création ${format.label}…"
                    operationProgress = 0f
                    Thread {
                        val result = runCatching {
                            ArchiveEngine.create(
                                sources,
                                archiveOutputDirectory(currentDir, archiveDestination),
                                "ZERNEX_${System.currentTimeMillis()}",
                                format,
                                password
                            )
                        }
                        Handler(Looper.getMainLooper()).post {
                            busy = false
                            val archiveMessage = result.fold(
                                { "Archive ${format.label} créée : ${it.name}" },
                                { "Compression impossible : ${it.message ?: "erreur inconnue"}" }
                            )
                            val preserved = if (deleteAfterZip && result.isSuccess) {
                                sources.filterNot { runCatching { moveToTrash(it, Environment.getExternalStorageDirectory()) }.getOrDefault(false) }
                            } else emptyList()
                            message = if (preserved.isEmpty()) archiveMessage else "$archiveMessage • ${preserved.size} original(aux) conservé(s) : corbeille inaccessible"
                            selected.clear()
                            refreshNow()
                        }
                    }.start()
                }
            ) { Text("Créer") }
        },
        dismissButton = {
            TextButton(onClick = { dialog = ""; archivePassword = ""; archiveFormatMenuExpanded = false }) { Text("Annuler") }
        }
    )

    if (dialog == "mkdir" || dialog == "rename" || dialog == "newtxt") AlertDialog(
        onDismissRequest = { dialog = "" },
        title = { Text(when (dialog) { "mkdir" -> "Créer un dossier"; "newtxt" -> "Créer un fichier texte"; else -> "Renommer" }) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(nameInput, { nameInput = it }, label = { Text("Nom") }, singleLine = true)
            Text("Évite / \\ et les noms déjà utilisés.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { TextButton(onClick = {
            val clean = nameInput.trim()
            val creating = dialog == "mkdir"
            val creatingText = dialog == "newtxt"
            val source = activeFile
            val finalName = if (creatingText && !clean.lowercase(Locale.ROOT).endsWith(".txt")) "$clean.txt" else clean
            val valid = isValidLocalLeafName(finalName)
            val parent = if (creating || creatingText) currentDir else source?.parentFile
            val target = if (valid && parent != null) File(parent, finalName) else null
            val sameTarget = source != null && target?.let { runCatching { it.canonicalFile == source.canonicalFile }.getOrDefault(false) } == true
            val collision = runCatching { target?.exists() == true && !sameTarget }.getOrDefault(true)
            val ok = runCatching {
                if (!valid || target == null || collision) false
                else if (creating) target.mkdir() else if (creatingText) target.createNewFile() else if (sameTarget) false else source!!.renameTo(target)
            }.getOrDefault(false)
            message = when { !valid -> "Nom invalide (255 octets maximum, sans séparateur)"; collision -> "Un élément porte déjà ce nom"; sameTarget -> "Le nouveau nom est identique à l'ancien"; ok -> when { creating -> "Dossier créé"; creatingText -> "Fichier texte créé"; else -> "Élément renommé" }; else -> "Opération impossible : vérifie les autorisations et l'espace disponible" }
            dialog = ""; activeFile = null; refreshNow()
        }) { Text("Valider") } },
        dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Annuler") } }
    )
    if (dialog == "recent") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Fichiers récents") }, text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val existing = recentPaths.map { File(it) }.filter { it.exists() }
        if (existing.isEmpty()) Text("Les fichiers ouverts apparaîtront ici.")
        existing.forEach { item -> ListItem(headlineContent = { Text(item.name, maxLines = 1) }, supportingContent = { Text("${formatSize(item.length())} • ${item.parent}", maxLines = 1) }, leadingContent = { FileTypeIcon(item, 38.dp) }, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { dialog = ""; if (item.parentFile != null) go(item.parentFile!!.absolutePath); rememberRecent(item) }, onLongClick = { propertiesFile = item; dialog = "" })) }
    } }, confirmButton = { TextButton(onClick = { dialog = "" }) { Text("Fermer") } }, dismissButton = { TextButton(onClick = { recentPaths = emptyList(); prefs.edit().remove("recent_paths").apply() }) { Text("Effacer") } })
    if (dialog == "bulk_rename") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Renommer la sélection") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ajoute un début et/ou une fin au nom de chaque élément sélectionné. Les extensions sont conservées.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(bulkRenamePrefix, { bulkRenamePrefix = it }, label = { Text("Début du nom") }, singleLine = true)
        OutlinedTextField(bulkRenameSuffix, { bulkRenameSuffix = it }, label = { Text("Fin du nom") }, singleLine = true)
        Text("${selectedLocalFiles.size} élément(s) sélectionné(s). Les dossiers sélectionnés incluent leurs sous-éléments ; ceux-ci ne seront pas renommés séparément.", style = MaterialTheme.typography.labelSmall)
        Text("Les extensions sont conservées. Tout conflit de nom bloque le lot avant modification.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }, confirmButton = { TextButton(enabled = !busy && clipboardPaths.isEmpty() && (bulkRenamePrefix.isNotBlank() || bulkRenameSuffix.isNotBlank()) && selectedLocalFiles.isNotEmpty(), onClick = {
        val sources = selectedLocalFiles.toList()
        val prefix = bulkRenamePrefix
        val suffix = bulkRenameSuffix
        dialog = ""
        busy = true
        operationLabel = "Renommage en cours…"
        Thread {
            val result = runCatching { renameLocalSelection(sources, prefix, suffix) }
            Handler(Looper.getMainLooper()).post {
                busy = false
                message = result.fold({ it.message }, { "Renommage impossible : ${it.message ?: "erreur inconnue"}" })
                refreshNow()
            }
        }.start()
    }) { Text("Renommer") } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Annuler") } })
    if (dialog == "favorites") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Dossiers favoris") }, text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) { favorites.forEach { path -> Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = { dialog = ""; go(path) }, modifier = Modifier.weight(1f)) { Text(path, maxLines = 1) }; IconButton(onClick = { favorites = favorites - path; prefs.edit().putStringSet("favorites", favorites.toSet()).apply() }) { Icon(Icons.Default.Close, "Retirer") } } } } }, confirmButton = { TextButton(onClick = { dialog = "" }) { Text("Fermer") } })
    if (dialog == "delete") AlertDialog(
        onDismissRequest = { dialog = "" },
        title = { Text("Confirmer la suppression") },
        text = { Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${selectedLocalFiles.size} élément(s) seront déplacés dans la corbeille. Tu pourras les restaurer plus tard.")
            selectedLocalFiles.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, null, tint = if (item.isDirectory) Color(0xFFC7C7C7) else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    if (item.isDirectory) Text("Dossier", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Text("Les éléments seront conservés dans la corbeille jusqu'à leur suppression définitive.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        } },
        confirmButton = { TextButton(onClick = {
            val root = runCatching { Environment.getExternalStorageDirectory().canonicalFile }.getOrNull()
            val here = runCatching { currentDir.canonicalFile }.getOrNull()
            var deleted = 0; val failures = mutableListOf<String>()
            selectedLocalFiles.forEach { selectedItem ->
                val path = selectedItem.absolutePath
                val item = runCatching { selectedItem.canonicalFile }.getOrNull()
                val systemProtectedRoots = listOf("/", "/system", "/vendor", "/product", "/apex", "/proc", "/sys", "/dev", "/data", "/metadata", "/odm", "/linkerconfig")
                val itemPath = item?.absolutePath?.trimEnd('/')?.ifBlank { "/" }
                val isSystemArea = itemPath != null && systemProtectedRoots.any { protectedRoot ->
                    itemPath == protectedRoot || (protectedRoot != "/" && itemPath.startsWith(protectedRoot + "/"))
                }
                val containsCurrentDirectory = item?.let { candidate ->
                    runCatching { candidate.isDirectory && here?.path?.startsWith(candidate.path + File.separator) == true }.getOrDefault(false)
                } == true
                val protected = item == null || item == root || item == here || isSystemArea || containsCurrentDirectory
                if (protected) failures += File(path).name + " (emplacement système ou racine protégé)"
                else {
                    val movedToTrash = item?.let { safeItem ->
                        runCatching { safeItem.exists() && moveToTrash(safeItem, Environment.getExternalStorageDirectory()) }.getOrDefault(false)
                    } == true
                    if (movedToTrash) deleted++ else failures += File(path).name
                }
            }
            favorites = favorites.filter { fav -> File(fav).exists() }
            prefs.edit().putStringSet("favorites", favorites.toSet()).apply()
            selected.clear(); message = if (failures.isEmpty()) "$deleted élément(s) déplacé(s) dans la corbeille" else "$deleted supprimé(s), échec : ${failures.take(3).joinToString() }"
            dialog = ""; refreshNow()
        }) { Text("Mettre à la corbeille") } },
        dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Annuler") } }
    )
    if (dialog == "actions" && activeFile != null) AlertDialog(
        onDismissRequest = { dialog = "" },
        title = { Text(activeFile!!.name, maxLines = 2) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileTypeIcon(activeFile!!, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column { Text(if (activeFile!!.isDirectory) "Dossier" else "Fichier", fontWeight = FontWeight.SemiBold); Text(formatSize(activeFile!!.length()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Choisis une action", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!activeFile!!.isDirectory) {
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val target = activeFile!!
                        dialog = ""
                        openWithExternal(context, target) { message = it }
                    }
                ) {
                    Icon(Icons.Default.OpenInNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Ouvrir avec une autre application")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(modifier = Modifier.weight(1f), enabled = clipboardPaths.isEmpty(), onClick = { clipboardPaths = listOf(activeFile!!.absolutePath); clipboardMove = false; dialog = "" }) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(5.dp)); Text("Copier") }
                FilledTonalButton(modifier = Modifier.weight(1f), enabled = clipboardPaths.isEmpty(), onClick = { clipboardPaths = listOf(activeFile!!.absolutePath); clipboardMove = true; dialog = "" }) { Icon(Icons.Default.ContentCut, null); Spacer(Modifier.width(5.dp)); Text("Couper") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(modifier = Modifier.weight(1f), onClick = { shareLocalFile(context, activeFile!!); dialog = "" }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(5.dp)); Text("Partager") }
                OutlinedButton(modifier = Modifier.weight(1f), enabled = clipboardPaths.isEmpty(), onClick = { nameInput = activeFile!!.name; dialog = "rename" }) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(5.dp)); Text("Renommer") }
            }
            TextButton(onClick = { propertiesFile = activeFile; dialog = "" }) { Icon(Icons.Default.Info, null); Spacer(Modifier.width(7.dp)); Text("Voir les détails") }
            TextButton(enabled = clipboardPaths.isEmpty(), onClick = { selected.clear(); selected.add(activeFile!!.absolutePath); dialog = "delete" }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(7.dp)); Text("Mettre à la corbeille") }
        } }, confirmButton = { TextButton(onClick = { dialog = "" }) { Text("Terminé") } }
    )
    if (dialog == "permissions") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Accès aux fichiers") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ZERNEX Files a besoin de ton autorisation pour afficher, organiser et gérer les fichiers de ton appareil.")
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (allFilesGranted.value) Icons.Default.CheckCircle else Icons.Default.Lock, null, tint = if (allFilesGranted.value) ZernexPalette.green else ZernexPalette.primary); Spacer(Modifier.width(8.dp)); Text(if (allFilesGranted.value) "Accès au stockage autorisé" else "Accès au stockage à autoriser") }
        Text("Les dossiers privés d'autres applications restent protégés par Android.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }, confirmButton = { TextButton(onClick = { dialog = ""; if (!allFilesGranted.value) requestStorage() }) { Text(if (allFilesGranted.value) "Compris" else "Autoriser") } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Fermer") } })
    if (dialog == "trash") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Corbeille (${trashItems.size})") }, text = { Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (trashItems.isEmpty()) Text("La corbeille est vide.")
        trashItems.forEach { entry -> Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (entry.file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, null); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(entry.file.name, maxLines = 1); Text("Emplacement d'origine : ${entry.originalPath}", style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) }; TextButton(onClick = { val ok = restoreFromTrash(entry); message = if (ok) "Élément restauré" else "Restauration impossible : emplacement déjà occupé"; trashItems = loadTrash(context); refreshNow() }) { Text("Restaurer") } } }
    } }, confirmButton = { TextButton(onClick = { dialog = "" }) { Text("Fermer") } }, dismissButton = { if (trashItems.isNotEmpty()) TextButton(onClick = { val count = trashItems.count { runCatching { it.file.deleteRecursively(); File(it.file.parentFile, it.file.name + ".origin").delete(); true }.getOrDefault(false) }; message = "$count élément(s) supprimé(s) définitivement"; trashItems = emptyList(); dialog = "" }) { Text("Vider la corbeille") } })
    if (dialog == "duplicates") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Fichiers en double") }, text = { Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (duplicateGroups.isEmpty()) Text("Aucun doublon trouvé.") else { Text("${duplicateGroups.sumOf { it.size - 1 }} copie(s) potentielle(s) trouvée(s). Vérifie les éléments avant toute suppression."); duplicateGroups.forEach { group -> Text("Même contenu • ${formatSize(group.first().length())}", fontWeight = FontWeight.SemiBold); group.forEach { Text("• ${it.absolutePath}", style = MaterialTheme.typography.bodySmall) } } }
    } }, confirmButton = { TextButton(onClick = { dialog = "" }) { Text("Terminer") } })
    if (dialog == "storage" && storageSummary != null) AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Espace de stockage") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Stockage interne", fontWeight = FontWeight.SemiBold)
        Text("Utilisé : ${formatSize(storageSummary!!.used)} sur ${formatSize(storageSummary!!.total)}")
        LinearProgressIndicator(progress = { storageSummary!!.used.toFloat() / storageSummary!!.total.coerceAtLeast(1L) }, modifier = Modifier.fillMaxWidth())
        Text("Disponible : ${formatSize(storageSummary!!.free)}", color = MaterialTheme.colorScheme.primary)
    } }, confirmButton = { TextButton(onClick = { dialog = "" }) { Text("Fermer") } })
}

@Composable
private fun FileDetailsPage(file: File, onBack: () -> Unit) {
    var md5 by remember(file.absolutePath, file.lastModified(), file.length()) { mutableStateOf(if (file.isDirectory) "Non disponible pour un dossier" else "Calcul en cours…") }
    var displayedSize by remember(file.absolutePath, file.lastModified(), file.length()) { mutableStateOf(if (file.isDirectory) "Calcul en cours…" else "${formatSize(file.length())} (${file.length()} octets)") }
    LaunchedEffect(file.absolutePath, file.lastModified(), file.length()) {
        if (file.isDirectory) displayedSize = withContext(Dispatchers.IO) { "${formatSize(directorySize(file))} (contenu du dossier)" }
        md5 = if (file.isDirectory) "Non disponible pour un dossier" else withContext(Dispatchers.IO) {
            runCatching {
                val digest = MessageDigest.getInstance("MD5")
                file.inputStream().buffered().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }.getOrElse { "Impossible à calculer" }
        }
    }
    val mime = if (file.isDirectory) "Dossier" else MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT)) ?: "Type inconnu"
    val modified = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(java.util.Date(file.lastModified()))
    val mode = runCatching { Integer.toOctalString(android.system.Os.stat(file.absolutePath).st_mode and 0x1FF) }.getOrNull()
    ZernexPageScaffold(title = "Informations", subtitle = file.name, onBack = onBack) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FileTypeIcon(file, 48.dp)
                    Column(Modifier.weight(1f)) {
                        Text(file.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(mime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DetailLine("Taille", displayedSize)
                DetailLine("Dernière modification", modified)
                DetailLine("Permissions", "${if (file.canRead()) "Lecture" else "Pas de lecture"} • ${if (file.canWrite()) "Écriture" else "Pas d'écriture"} • ${if (file.canExecute()) "Exécution" else "Pas d'exécution"}${mode?.let { " ($it)" } ?: ""}")
                DetailLine("Chemin", file.absolutePath)
                DetailLine("Emplacement", if (file.absolutePath.startsWith(Environment.getExternalStorageDirectory().absolutePath)) "Stockage partagé" else "Stockage de l'application ou autre emplacement")
                DetailLine("État", if (file.exists()) "Disponible" else "Introuvable")
                DetailLine("MD5", md5)
            }
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
    }
}

@Composable
internal fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun directorySize(directory: File): Long {
    var total = 0L
    val stack = ArrayDeque<File>()
    stack.add(directory)
    while (stack.isNotEmpty()) {
        val current = stack.removeLast()
        val children = runCatching { current.listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())
        children.forEach { child -> if (child.isDirectory) stack.add(child) else total += child.length() }
    }
    return total
}

private data class TrashEntry(val file: File, val originalPath: String)
private data class StorageSummary(val used: Long, val total: Long, val free: Long)

private fun trashDirectory(root: File) = File(root, ".ZERNEX_Corbeille")
private fun moveToTrash(source: File, root: File): Boolean {
    val canonical = source.canonicalFile
    require(canonical != root.canonicalFile && !canonical.path.startsWith(trashDirectory(root).canonicalPath + File.separator))
    val trash = trashDirectory(root)
    if (!trash.exists() && !trash.mkdirs()) return false
    val target = File(trash, "${System.currentTimeMillis()}_${canonical.name}")
    val moved = canonical.renameTo(target) || (if (canonical.isDirectory) canonical.copyRecursively(target, false).let { canonical.deleteRecursively() } else canonical.copyTo(target, false).let { canonical.delete() })
    if (moved) File(trash, target.name + ".origin").writeText(canonical.absolutePath)
    return moved
}
private fun loadTrash(context: Context): List<TrashEntry> {
    val root = Environment.getExternalStorageDirectory()
    return trashDirectory(root).listFiles()?.filter { it.isFile || it.isDirectory }?.filterNot { it.name.endsWith(".origin") }?.mapNotNull { file ->
        val meta = File(file.parentFile, file.name + ".origin")
        if (meta.exists()) TrashEntry(file, runCatching { meta.readText() }.getOrDefault("")) else null
    }?.sortedByDescending { it.file.lastModified() } ?: emptyList()
}
private fun restoreFromTrash(entry: TrashEntry): Boolean {
    val original = File(entry.originalPath)
    if (entry.originalPath.isBlank() || original.exists() || !original.parentFile?.exists()!!) return false
    val parent = original.parentFile ?: return false
    if (!parent.exists()) return false
    val moved = entry.file.renameTo(original) || (if (entry.file.isDirectory) entry.file.copyRecursively(original, false).let { entry.file.deleteRecursively() } else entry.file.copyTo(original, false).let { entry.file.delete() })
    if (moved) File(entry.file.parentFile, entry.file.name + ".origin").delete()
    return moved
}
private fun readStorageSummary(root: File): StorageSummary {
    val stat = android.os.StatFs(root.absolutePath); val total = stat.totalBytes; val free = stat.availableBytes
    return StorageSummary(total - free, total, free)
}
private fun findDuplicateGroups(root: File): List<List<File>> {
    val bySize = mutableMapOf<Long, MutableList<File>>()
    val stack = ArrayDeque<File>(); stack.add(root)
    var visited = 0
    while (stack.isNotEmpty() && visited < 12000) {
        val dir = stack.removeLast(); val children = dir.listFiles() ?: continue
        children.forEach { f -> if (f.name != ".ZERNEX_Corbeille") { if (f.isDirectory) stack.add(f) else if (f.isFile && f.length() > 0) bySize.getOrPut(f.length()) { mutableListOf() }.add(f); visited++ } }
    }
    return bySize.values.filter { it.size > 1 }.flatMap { candidates -> candidates.groupBy { file -> runCatching { file.inputStream().buffered().use { input -> val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(64 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }; digest.digest().joinToString("") { b -> "%02x".format(b) } } }.getOrDefault(file.absolutePath) }.values.filter { it.size > 1 } }
}

private fun archiveOutputDirectory(current: File, mode: String): File {
    if (mode != "archives") return current
    val folder = File(current, "Archives")
    require((folder.exists() && folder.isDirectory) || folder.mkdirs()) { "Impossible de créer le dossier Archives" }
    return folder
}

private fun createZipArchive(files: List<File>, destination: File): File {
    require(files.isNotEmpty()) { "Aucun fichier sélectionné" }
    require(destination.exists() || destination.mkdirs()) { "Dossier de destination inaccessible" }
    val dest = destination.canonicalFile
    val canonicalInputs = files.map { it.canonicalFile }
    require(canonicalInputs.distinct().size == canonicalInputs.size) { "La sélection contient des doublons" }
    require(files.none { it.absoluteFile != it.canonicalFile }) { "Les liens symboliques ne sont pas inclus dans les archives" }
    require(canonicalInputs.none { it.isDirectory && (dest == it || dest.path.startsWith(it.path + File.separator)) }) {
        "Choisis un dossier d'archives situé hors des dossiers à compresser"
    }
    val finalArchive = File(dest, "ZERNEX_${System.currentTimeMillis()}.zip")
    val tempArchive = File(dest, finalArchive.name + ".part")
    val names = mutableSetOf<String>()
    try {
        ZipOutputStream(tempArchive.outputStream().buffered()).use { zip ->
            fun add(file: File, base: String) {
                val canonical = file.canonicalFile
                if (file.absoluteFile != canonical) return // ignore symbolic links
                val name = if (base.isEmpty()) file.name else "$base/${file.name}"
                if (file.isDirectory) {
                    require(names.add("$name/")) { "Noms en double dans la sélection : $name" }
                    zip.putNextEntry(ZipEntry("$name/")); zip.closeEntry()
                    (file.listFiles() ?: throw IllegalStateException("Dossier illisible : ${file.name}")).forEach { add(it, name) }
                } else if (file.isFile) {
                    require(names.add(name)) { "Noms en double dans la sélection : $name" }
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            files.forEach { add(it, "") }
        }
        require(tempArchive.renameTo(finalArchive)) { "Impossible de finaliser l'archive" }
        return finalArchive
    } catch (error: Throwable) {
        tempArchive.delete()
        throw error
    }
}

private fun openWithExternal(context: Context, file: File, onError: (String) -> Unit = {}) {
    if (!file.exists() || !file.isFile) {
        onError("Ce fichier n'est plus disponible.")
        return
    }
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val handlers = context.packageManager.queryIntentActivities(intent, 0)
        if (handlers.isEmpty()) {
            onError("Aucune application compatible trouvée pour ce fichier.")
        } else {
            context.startActivity(Intent.createChooser(intent, "Ouvrir ${file.name}"))
        }
    }.onFailure {
        onError("Impossible d'ouvrir ce fichier. Vérifie qu'une application compatible est installée.")
    }
}

private fun shareLocalFiles(context: Context, files: List<File>) {
    val valid = files.filter { it.isFile && it.exists() }
    if (valid.isEmpty()) return
    runCatching {
        val uris = ArrayList<Uri>()
        valid.forEach { uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)) }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Partager ${valid.size} élément(s)"))
    }.onFailure { Toast.makeText(context, "Impossible de partager ces éléments", Toast.LENGTH_SHORT).show() }
}

private fun shareLocalFile(context: Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(uri) ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Partager ${file.name}"))
    }
}


@Composable
internal fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp), content = content)
    }
}

@Composable
internal fun SettingsSwitchRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsHint(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(9.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class LocalRenameResult(val message: String)

private fun isValidLocalLeafName(name: String): Boolean {
    val clean = name.trim()
    return clean.isNotEmpty() && clean !in setOf(".", "..") &&
        !clean.contains('/') && !clean.contains('\\') && !clean.contains('\u0000') &&
        clean.toByteArray(Charsets.UTF_8).size <= 255
}

private fun collapseLocalSelection(files: List<File>): List<File> {
    val canonical = files.mapNotNull { file -> runCatching { file.canonicalFile }.getOrNull() }.distinctBy { it.path }
    val directories = canonical.filter { runCatching { it.isDirectory }.getOrDefault(false) }
    return canonical.filter { candidate ->
        directories.none { parent ->
            parent.path != candidate.path && candidate.path.startsWith(parent.path.trimEnd(File.separatorChar) + File.separator)
        }
    }
}

private fun renamedLocalName(file: File, prefix: String, suffix: String): String {
    val isRegularFile = runCatching { file.isFile }.getOrDefault(false)
    val extension = if (isRegularFile && file.extension.isNotEmpty() && file.name.lastIndexOf('.') > 0) ".${file.extension}" else ""
    val stem = if (extension.isNotEmpty()) file.name.removeSuffix(extension) else file.name
    return prefix.trim() + stem + suffix.trim() + extension
}

private fun renameLocalSelection(files: List<File>, prefix: String, suffix: String): LocalRenameResult {
    if (prefix.isBlank() && suffix.isBlank()) return LocalRenameResult("Ajoute un début ou une fin au nom.")
    val sources = collapseLocalSelection(files)
    if (sources.isEmpty()) return LocalRenameResult("Aucun élément valide à renommer.")
    data class Plan(val source: File, val target: File)
    val plans = mutableListOf<Plan>()
    val sourcePaths = sources.map { it.path }.toSet()
    val targetPaths = mutableSetOf<String>()
    for (source in sources) {
        val parent = source.parentFile ?: return LocalRenameResult("Dossier parent indisponible pour ${source.name}.")
        if (!runCatching { source.exists() }.getOrDefault(false)) return LocalRenameResult("Un élément sélectionné n'existe plus ou n'est plus accessible : ${source.name}")
        val targetName = renamedLocalName(source, prefix, suffix)
        if (!isValidLocalLeafName(targetName)) return LocalRenameResult("Nom refusé pour ${source.name} : nom vide, séparateur ou plus de 255 octets.")
        val target = File(parent, targetName)
        val sameTarget = runCatching { target.canonicalFile == source.canonicalFile }.getOrDefault(false)
        if (sameTarget) continue
        val key = runCatching { target.canonicalPath.lowercase(Locale.ROOT) }.getOrElse { target.absolutePath.lowercase(Locale.ROOT) }
        if (!targetPaths.add(key)) return LocalRenameResult("Deux éléments produiraient le même nom : $targetName. Aucun changement effectué.")
        val targetExists = runCatching { target.exists() }.getOrElse { return LocalRenameResult("Impossible de vérifier le nom ${target.name} ; aucun changement effectué.") }
        if (targetExists) {
            val existingPath = runCatching { target.canonicalPath }.getOrNull()
            if (existingPath == null) return LocalRenameResult("Impossible de vérifier le conflit ${target.name}. Aucun changement effectué.")
            if (!sourcePaths.contains(existingPath)) return LocalRenameResult("Le nom ${target.name} existe déjà. Aucun changement effectué.")
            return LocalRenameResult("Le renommage croisé vers ${target.name} est refusé pour éviter de remplacer un élément sélectionné.")
        }
        plans.add(Plan(source, target))
    }
    if (plans.isEmpty()) return LocalRenameResult("Aucun nom n'a changé.")
    val completed = mutableListOf<Plan>()
    for (plan in plans) {
        // Recheck immediately before rename to reduce the chance of replacing a file
        // created by another app after the initial preflight.
        val destinationNowExists = runCatching { plan.target.exists() }.getOrDefault(true)
        val ok = !destinationNowExists && runCatching { plan.source.renameTo(plan.target) }.getOrDefault(false)
        if (!ok) {
            val rollbackFailures = completed.asReversed().filterNot { runCatching { it.target.renameTo(it.source) }.getOrDefault(false) }
            return if (rollbackFailures.isEmpty()) LocalRenameResult("Renommage annulé : destination occupée ou accès refusé pour ${plan.source.name}. Les éléments précédents ont été restaurés.")
            else LocalRenameResult("Renommage partiel : ${rollbackFailures.size} élément(s) n'ont pas pu être restaurés. Vérifie le dossier.")
        }
        completed.add(plan)
    }
    return LocalRenameResult("${completed.size} élément(s) renommé(s) avec succès.")
}

private data class LocalTransferResult(
    val completedPaths: Set<String>,
    val warnings: List<String>,
    val skipped: Int,
    val cancelled: Boolean
)

private fun uniqueLocalTarget(parent: File, requestedName: String): File {
    val dot = requestedName.lastIndexOf('.')
    val hasExtension = dot > 0
    val stem = if (hasExtension) requestedName.substring(0, dot) else requestedName
    val extension = if (hasExtension) requestedName.substring(dot) else ""
    var candidate = File(parent, requestedName)
    var suffix = 1
    while (candidate.exists()) {
        if (suffix > 9999) error("Trop de fichiers portent déjà un nom similaire")
        candidate = File(parent, "$stem (copie $suffix)$extension")
        suffix++
    }
    return candidate
}

private data class LocalCopyBudget(var entries: Int = 0, var bytes: Long = 0L)

private fun copyLocalTreeSafely(
    source: File,
    target: File,
    cancel: java.util.concurrent.atomic.AtomicBoolean,
    budget: LocalCopyBudget,
    depth: Int = 0
) {
    if (cancel.get()) throw InterruptedException("Opération interrompue")
    if (depth > 64) error("Dossier trop profond pour être copié en toute sécurité")
    budget.entries++
    if (budget.entries > 50000) error("Limite de 50 000 éléments atteinte")
    if (source.isDirectory) {
        if (!target.mkdir()) error("Impossible de créer le dossier ${target.name}")
        val children = source.listFiles() ?: error("Lecture refusée dans ${source.name}")
        children.forEach { child -> copyLocalTreeSafely(child, File(target, child.name), cancel, budget, depth + 1) }
    } else {
        if (!target.createNewFile()) error("Le fichier de destination existe déjà : ${target.name}")
        source.inputStream().buffered().use { input ->
            target.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    if (cancel.get()) throw InterruptedException("Opération interrompue")
                    val count = input.read(buffer)
                    if (count < 0) break
                    budget.bytes += count
                    if (budget.bytes > 8L * 1024 * 1024 * 1024) error("Limite de copie de 8 Gio atteinte")
                    output.write(buffer, 0, count)
                }
                output.flush()
            }
        }
    }
}

private fun transferLocalFiles(
    paths: List<String>,
    destination: File,
    move: Boolean,
    cancel: java.util.concurrent.atomic.AtomicBoolean,
    onProgress: (Int, Int) -> Unit
): LocalTransferResult {
    val completed = linkedSetOf<String>()
    val warnings = mutableListOf<String>()
    var skipped = 0
    var cancelled = false
    val budget = LocalCopyBudget()
    val targetDir = runCatching { destination.canonicalFile }.getOrElse { return LocalTransferResult(emptySet(), listOf("Dossier de destination inaccessible"), 0, false) }
    if (!runCatching { targetDir.isDirectory && targetDir.canWrite() }.getOrDefault(false)) {
        return LocalTransferResult(emptySet(), listOf("Le dossier de destination n'est pas accessible en écriture"), 0, false)
    }
    val sources = collapseLocalSelection(paths.map { File(it) })
    val originalByCanonical = paths.associateBy { runCatching { File(it).canonicalPath }.getOrDefault(it) }
    for ((index, sourceUncanonical) in sources.withIndex()) {
        val source = try {
            sourceUncanonical.canonicalFile
        } catch (_: Exception) {
            warnings.add("Chemin invalide : ${sourceUncanonical.name}")
            continue
        }
        val originalPath = originalByCanonical[source.path] ?: sourceUncanonical.absolutePath
        onProgress(index + 1, sources.size)
        if (cancel.get()) { cancelled = true; break }
        val readable = runCatching { source.exists() && source.canRead() }.getOrDefault(false)
        if (!readable) { warnings.add("Source inaccessible : ${source.name}"); continue }
        val directoryCheck = runCatching { source.isDirectory }
        if (directoryCheck.isFailure) {
            warnings.add("Type de source impossible à vérifier : ${source.name}")
            continue
        }
        val sourceIsDirectory = directoryCheck.getOrDefault(false)
        val sourcePath = source.path.trimEnd(File.separatorChar)
        val targetPath = targetDir.path.trimEnd(File.separatorChar)
        if (sourceIsDirectory && (targetPath == sourcePath || targetPath.startsWith(sourcePath + File.separator))) {
            warnings.add("Destination située dans le dossier source : ${source.name}")
            continue
        }
        val sameParent = runCatching { source.parentFile?.canonicalFile == targetDir }.getOrDefault(false)
        if (move && sameParent) {
            skipped++
            completed.add(originalPath)
            continue
        }
        var target: File? = null
        try {
            target = uniqueLocalTarget(targetDir, source.name)
            copyLocalTreeSafely(source, target, cancel, budget)
            if (move && !source.deleteRecursively()) warnings.add("Copie créée, mais source conservée : ${source.name}")
            completed.add(originalPath)
        } catch (error: Exception) {
            target?.let { runCatching { it.deleteRecursively() } }
            if (error is InterruptedException || cancel.get()) { cancelled = true; break }
            warnings.add("${source.name} : ${error.message ?: "échec de copie"}")
            break
        }
    }
    return LocalTransferResult(completed, warnings, skipped, cancelled)
}

private fun fileAccent(file: File): Color = zernexAccentFor(file.name, file.isDirectory)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListRow(file: File, isSelected: Boolean, compact: Boolean, showSize: Boolean, showDate: Boolean, enabled: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val ext = file.extension.lowercase(Locale.ROOT)
    val accent = fileAccent(file)
    val icon = fileVisualIcon(file)
    val scale by animateFloatAsState(if (isSelected) 1.045f else 1f, label = "file-row-scale")
    val modified = remember(file.absolutePath, file.lastModified()) {
        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(java.util.Date(file.lastModified()))
    }
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(15.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.62f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f), RoundedCornerShape(15.dp)).animateContentSize()
    ) {
        Row(
            Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
                .combinedClickable(onClick = { if (enabled) onClick() }, onLongClick = { if (enabled) onLongClick() })
                .padding(start = 4.dp, end = 3.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileTypeIcon(file = file, size = 48.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(file.name, maxLines = 1, style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (file.isDirectory) FontWeight.SemiBold else FontWeight.Medium)
                Text(
                    if (file.isDirectory) {
                        val count = remember(file.absolutePath, file.lastModified()) { runCatching { file.list()?.size ?: 0 }.getOrDefault(0) }
                        if (count == 0) "Dossier vide" else "$count éléments"
                    } else listOfNotNull(ext.uppercase().ifBlank { "FICHIER" }.takeIf { showSize.not() }, formatSize(file.length()).takeIf { showSize }).joinToString(" • "),
                    maxLines = 1, style = MaterialTheme.typography.labelSmall,
                    color = accent.copy(alpha = 0.95f)
                )
                if (showDate) Text(modified, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (isSelected) Icon(Icons.Default.CheckCircle, "Sélectionné", tint = accent, modifier = Modifier.size(22.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridCard(file: File, isSelected: Boolean, showSize: Boolean, showDate: Boolean, enabled: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val accent = fileAccent(file)
    val scale by animateFloatAsState(if (isSelected) 1.04f else 1f, label = "file-grid-scale")
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.62f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f), RoundedCornerShape(16.dp)).animateContentSize().graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Column(
            Modifier.fillMaxWidth().combinedClickable(onClick = { if (enabled) onClick() }, onLongClick = { if (enabled) onLongClick() }).padding(11.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                if (!file.isDirectory && file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")) {
                    AsyncImage(model = file, contentDescription = file.name, modifier = Modifier.size(70.dp).clip(RoundedCornerShape(15.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                } else FileTypeIcon(file = file, size = 70.dp, large = true)
                if (isSelected) Icon(Icons.Default.CheckCircle, "Sélectionné", tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(9.dp))
            Text(file.name, maxLines = 2, minLines = 2, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold)
            if (showSize) Text(if (file.isDirectory) "Dossier" else formatSize(file.length()),
                style = MaterialTheme.typography.labelSmall, color = accent)
            if (showDate) Text(java.text.SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(java.util.Date(file.lastModified())), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FileTypeIcon(file: File, size: androidx.compose.ui.unit.Dp, large: Boolean = false) {
    val accent = fileAccent(file)
    val icon = fileVisualIcon(file)
    Box(
        Modifier.size(size)
            .clip(RoundedCornerShape(if (large) 15.dp else 12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = if (file.isDirectory) MaterialTheme.colorScheme.outlineVariant else accent.copy(alpha = 0.42f),
                shape = RoundedCornerShape(if (large) 15.dp else 12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (file.isDirectory) "Dossier" else "Fichier ${file.extension}",
            tint = if (file.isDirectory) MaterialTheme.colorScheme.onSurface else accent,
            modifier = Modifier.size(if (file.isDirectory) size * 0.70f else size * 0.58f)
        )
        if (!file.isDirectory) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(5.dp)
                    .size(if (large) 7.dp else 6.dp)
                    .background(accent, RoundedCornerShape(50))
            )
        }
    }
}

private fun fileVisualIcon(file: File): androidx.compose.ui.graphics.vector.ImageVector {
    if (file.isDirectory) return when (file.name.lowercase(Locale.ROOT)) {
        "dcim", "pictures", "images" -> Icons.Default.PhotoLibrary
        "music", "podcasts" -> Icons.Default.LibraryMusic
        "movies", "videos" -> Icons.Default.VideoLibrary
        "download", "downloads" -> Icons.Default.Download
        "documents" -> Icons.Default.FolderCopy
        "android" -> Icons.Default.Android
        "alarms" -> Icons.Default.Alarm
        "bluetooth" -> Icons.Default.Bluetooth
        "notifications" -> Icons.Default.Notifications
        "ringtones" -> Icons.Default.MusicNote
        else -> Icons.Default.Folder
    }
    return when (file.extension.lowercase(Locale.ROOT)) {
        "pdf" -> Icons.Default.PictureAsPdf
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "svg" -> Icons.Default.Image
        "mp4", "mkv", "webm", "mov", "avi" -> Icons.Default.Movie
        "mp3", "wav", "ogg", "flac", "m4a" -> Icons.Default.AudioFile
        "zip", "7z", "rar", "tar", "gz", "jar" -> Icons.Default.FolderZip
        "apk", "aab" -> Icons.Default.Android
        "kt", "java", "js", "ts", "py", "html", "css", "xml", "yml", "yaml", "json", "gradle" -> Icons.Default.Code
        "doc", "docx", "odt", "rtf" -> Icons.Default.Description
        "xls", "xlsx", "csv", "ods" -> Icons.Default.TableChart
        "ppt", "pptx", "odp" -> Icons.Default.Slideshow
        "txt", "md", "log" -> Icons.Default.TextSnippet
        else -> Icons.Default.InsertDriveFile
    }
}

private fun openFile(file: File, onPreview: () -> Unit, onEdit: () -> Unit) {
    when (file.extension.lowercase()) {
        "txt", "md", "json", "xml", "html", "htm", "css", "js", "ts", "kt", "java", "py", "sh", "yml", "yaml", "toml", "ini", "properties", "gradle", "sql", "c", "cpp", "h", "rs", "go", "dart", "php", "rb", "swift", "log", "conf" -> onEdit()
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "pdf", "mp4", "mkv", "webm", "mov", "mp3", "wav", "ogg", "flac", "m4a", "jks", "keystore", "jceks", "p12", "pfx", "bks", "pem", "crt", "cer", "der", "zip", "7z", "tar", "tgz", "gz", "bz2", "xz", "tbz", "tbz2", "txz" -> onPreview()
        else -> onPreview()
    }
}

private fun saveTextSafely(file: File, content: String) {
    val parent = file.parentFile ?: error("Dossier parent introuvable")
    val temp = File(parent, ".${file.name}.zernex-tmp")
    try {
        temp.outputStream().buffered().use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
            output.flush()
        }
        if (!temp.renameTo(file)) error("Remplacement atomique impossible ; fichier original conservé")
    } finally {
        if (temp.exists()) temp.delete()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileEditorScreen(file: File, initial: String, onText: (String) -> Unit, onSave: (String) -> Boolean, onClose: () -> Unit) {
    var text by remember(file) { mutableStateOf(initial) }
    var dirty by remember(file, initial) { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text("${file.extension.uppercase().ifBlank { "TEXTE" }} • ${text.lines().size} lignes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Retour") } },
                actions = {
                    TextButton(enabled = dirty, onClick = { onText(text); if (onSave(text)) dirty = false }) {
                        Icon(Icons.Default.Save, null); Spacer(Modifier.width(5.dp)); Text("Enregistrer")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(file.absolutePath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Surface(Modifier.weight(1f).fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; dirty = true; onText(it) },
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface),
                    visualTransformation = SyntaxColorTransformation(file.extension),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
                    placeholder = { Text("Commencer à écrire…") }
                )
            }
            if (dirty) Text("Modifications non enregistrées", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

private class SyntaxColorTransformation(private val extension: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val keywords = when (extension.lowercase()) {
            "kt", "kts", "java", "js", "ts", "c", "cpp", "h", "py", "rs", "go", "swift", "php", "dart" -> setOf("class","fun","function","return","if","else","for","while","import","package","public","private","static","void","val","var","const","let","new","true","false","null","try","catch","throw","async","await","object","interface","extends","implements","def","self","struct","enum","data","when","is","as","this","super")
            "json" -> setOf("true","false","null")
            "xml", "html", "htm" -> setOf("DOCTYPE","html","head","body","div","span","script","style")
            else -> emptySet()
        }
        val out = buildAnnotatedString {
            append(text)
            val source = text.text
            val wordRegex = Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\b")
            wordRegex.findAll(source).forEach { m -> if (m.value in keywords) addStyle(SpanStyle(color = Color(0xFF38C978), fontWeight = FontWeight.Bold), m.range.first, m.range.last + 1) }
            Regex("//[^\\n]*|#[^\\n]*").findAll(source).forEach { addStyle(SpanStyle(color = Color(0xFF858585)), it.range.first, it.range.last + 1) }
            Regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'").findAll(source).forEach { addStyle(SpanStyle(color = Color(0xFFD6D6D6)), it.range.first, it.range.last + 1) }
            Regex("\\b[0-9]+(\\.[0-9]+)?\\b").findAll(source).forEach { addStyle(SpanStyle(color = Color(0xFFAAAAAA)), it.range.first, it.range.last + 1) }
        }
        return TransformedText(out, OffsetMapping.Identity)
    }
}

@Composable
private fun KeystoreFilePreview(file: File) {
    var password by remember(file) { mutableStateOf("") }
    var report by remember(file) { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("INSPECTEUR KEYSTORE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(file.absolutePath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Mot de passe (laisser vide si aucun)") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), singleLine = true)
        Button(enabled = !loading, onClick = {
            loading = true
            val pass = password.toCharArray(); password = ""
            Thread {
                val result = runCatching { inspectKeyStoreFile(file, pass) }.fold({ it }, { "Lecture impossible : ${it.message ?: "format non reconnu ou mot de passe incorrect"}" })
                Handler(Looper.getMainLooper()).post { report = result; loading = false }
            }.start()
        }) { Text(if (loading) "Inspection…" else "Inspecter le fichier") }
        if (report.isNotBlank()) Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) { Text(report, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
        Text("Le mot de passe reste local et n'est pas enregistré. Un keystore privé ne doit jamais être partagé.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun inspectKeyStoreFile(file: File, password: CharArray): String {
    try {
        require(file.isFile && file.canRead()) { "Fichier non lisible" }
        require(file.length() <= 32L * 1024 * 1024) { "Fichier supérieur à 32 Mio" }
        val types = when (file.extension.lowercase()) {
            "jks", "keystore" -> listOf("JKS", "PKCS12", "JCEKS")
            "p12", "pfx" -> listOf("PKCS12", "JKS", "JCEKS")
            "bks" -> listOf("BKS", "JKS", "PKCS12", "JCEKS")
            "jceks" -> listOf("JCEKS", "JKS", "PKCS12")
            else -> listOf("PKCS12", "JKS", "JCEKS")
        }
        var last: Throwable? = null
        for (type in types) {
            try {
                val store = if (type == "BKS") KeyStore.getInstance(type, BouncyCastleProvider()) else KeyStore.getInstance(type)
                file.inputStream().use { store.load(it, password) }
                val aliases = store.aliases().toList()
                return buildString {
                    appendLine("Format : $type")
                    appendLine("Entrées : ${aliases.size}")
                    aliases.forEach { alias ->
                        appendLine("\nAlias : $alias")
                        appendLine("Type : ${if (store.isKeyEntry(alias)) "Entrée de clé" else "Certificat"}")
                        appendLine("Clé privée : ${store.isKeyEntry(alias)}")
                        val certs = store.getCertificateChain(alias)?.toList() ?: listOfNotNull(store.getCertificate(alias))
                        certs.forEachIndexed { index, certificate ->
                            val x509 = certificate as? X509Certificate
                            appendLine("Certificat ${index + 1} : ${certificate.type}")
                            if (x509 != null) {
                                appendLine("Sujet : ${x509.subjectX500Principal.name}")
                                appendLine("Émetteur : ${x509.issuerX500Principal.name}")
                                appendLine("Valide du : ${x509.notBefore}")
                                appendLine("Valide jusqu'au : ${x509.notAfter}")
                                appendLine("Algorithme : ${x509.sigAlgName}")
                                appendLine("Numéro de série : ${x509.serialNumber.toString(16)}")
                            }
                            val fingerprint = MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString(":") { "%02X".format(it) }
                            appendLine("SHA-256 : $fingerprint")
                        }
                    }
                }
            } catch (error: Throwable) { last = error }
        }
        throw IllegalArgumentException("Aucun format JKS/PKCS12/BKS reconnu ; vérifie le mot de passe", last)
    } finally { password.fill('\u0000') }
}

@Composable
private fun CertificateFilePreview(file: File) {
    val report = remember(file) {
        runCatching {
            val certificate = file.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate }
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString(":") { "%02X".format(it) }
            "Sujet : ${certificate.subjectX500Principal.name}\nÉmetteur : ${certificate.issuerX500Principal.name}\nValide du : ${certificate.notBefore}\nValide jusqu'au : ${certificate.notAfter}\nSignature : ${certificate.sigAlgName}\nSHA-256 : $fingerprint"
        }.getOrElse { "Certificat illisible : ${it.message}" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { Text("CERTIFICAT X.509", fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Text(report, style = MaterialTheme.typography.bodySmall) }
}

/** Extracts a ZIP without overwriting existing files or allowing path traversal.
 * Partial output is rolled back if an entry fails validation or extraction.
 */
private fun extractZipSafely(archive: File, destination: File) {
    require(archive.isFile && archive.canRead()) { "Archive inaccessible" }
    val dest = destination.canonicalFile
    require(dest != archive.canonicalFile) { "Destination invalide" }
    require(!dest.exists() || (dest.isDirectory && dest.listFiles().isNullOrEmpty())) { "Le dossier de destination n'est pas vide" }
    require(dest.exists() || dest.mkdirs()) { "Impossible de créer le dossier de destination" }
    val created = mutableListOf<File>()
    val seen = mutableSetOf<String>()
    var totalBytes = 0L
    var entriesCount = 0
    try {
        ZipInputStream(archive.inputStream().buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                entriesCount++
                require(entriesCount <= 10000) { "Trop d'entrées dans l'archive (maximum 10 000)" }
                val rawName = entry.name.replace('\\', '/')
                require(rawName.isNotBlank() && !rawName.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(rawName)) { "Chemin d'archive invalide" }
                val output = File(dest, rawName).canonicalFile
                require(output.path.startsWith(dest.path + File.separator)) { "Chemin d'extraction dangereux" }
                val relative = output.relativeTo(dest).invariantSeparatorsPath
                require(seen.add(relative)) { "Entrée en double : $relative" }
                if (entry.isDirectory || rawName.endsWith('/')) {
                    require(!output.exists() || output.isDirectory) { "Conflit de fichier : $relative" }
                    if (!output.exists()) { require(output.mkdirs()) { "Création impossible : $relative" }; created.add(output) }
                } else {
                    require(!output.exists()) { "Un fichier existe déjà : $relative" }
                    val parent = output.parentFile ?: error("Dossier parent invalide")
                    require(parent.exists() || parent.mkdirs()) { "Création du dossier impossible" }
                    created.add(output)
                    output.outputStream().buffered().use { out ->
                        val buffer = ByteArray(16 * 1024)
                        var entryBytes = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            entryBytes += count
                            totalBytes += count
                            require(entryBytes <= 256L * 1024L * 1024L) { "Un fichier dépasse la limite de 256 Mio" }
                            require(totalBytes <= 512L * 1024L * 1024L) { "Limite totale d'extraction de 512 Mio dépassée" }
                            out.write(buffer, 0, count)
                        }
                    }
                }
                input.closeEntry()
            }
        }
    } catch (error: Throwable) {
        created.asReversed().forEach { runCatching { if (it.isDirectory) it.deleteRecursively() else it.delete() } }
        if (dest.exists() && dest.listFiles().isNullOrEmpty()) dest.delete()
        throw error
    }
}

@Composable
private fun ArchivePreview(file: File) {
    val scope = rememberCoroutineScope()
    var entries by remember(file) { mutableStateOf<List<ArchiveEntryInfo>>(emptyList()) }
    var message by remember(file) { mutableStateOf("") }
    var loading by remember(file) { mutableStateOf(true) }
    var extracting by remember(file) { mutableStateOf(false) }
    var showExtractDialog by remember(file) { mutableStateOf(false) }
    var password by remember(file) { mutableStateOf("") }

    LaunchedEffect(file) {
        val result = withContext(Dispatchers.IO) { runCatching { ArchiveEngine.list(file) } }
        entries = result.getOrElse { message = "Lecture de l’archive impossible : ${it.message ?: "format invalide"}"; emptyList() }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("${entries.size} entrée(s)", style = MaterialTheme.typography.titleSmall)
                Text("ZIP, 7Z, TAR, TAR.GZ, TAR.BZ2 et TAR.XZ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(enabled = !loading && !extracting, onClick = { showExtractDialog = true }) {
                Text(if (extracting) "Extraction…" else "Extraire ici")
            }
        }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (loading || extracting) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!loading && entries.isEmpty() && message.isBlank()) {
            ZernexEmptyState("Archive vide", "Aucun élément n’a été trouvé dans cette archive.") {
                Icon(Icons.Default.FolderZip, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            }
        }
        LazyColumn {
            items(entries, key = { it.name }) { entry ->
                ListItem(
                    headlineContent = { Text(entry.name, maxLines = 1) },
                    supportingContent = { Text(if (entry.isDirectory) "Dossier" else if (entry.size >= 0) formatSize(entry.size) else "Taille inconnue") },
                    leadingContent = { Icon(if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, null) }
                )
            }
        }
    }

    if (showExtractDialog) AlertDialog(
        onDismissRequest = { if (!extracting) showExtractDialog = false },
        title = { Text("Extraire l’archive") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Les éléments seront extraits dans un nouveau dossier nommé d’après l’archive. Les fichiers existants ne seront jamais écrasés.")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mot de passe (si requis)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Text("Limites de sécurité : 10 000 entrées, 256 Mio par fichier et 512 Mio au total.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = !extracting, onClick = {
                showExtractDialog = false
                extracting = true
                val secret = password.takeIf { it.isNotBlank() }
                val suffix = listOf(".tar.bz2", ".tar.gz", ".tar.xz", ".tbz2", ".tgz", ".txz").firstOrNull { file.name.endsWith(it, true) }
                val folderName = if (suffix != null) file.name.dropLast(suffix.length) else file.name.substringBeforeLast('.', file.name)
                val target = File(file.parentFile ?: file.absoluteFile.parentFile!!, folderName.ifBlank { "Archive extraite" })
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { ArchiveEngine.extractSafely(file, target, secret) } }
                    extracting = false
                    message = result.fold(
                        { "Extraction terminée : $it élément(s) dans ${target.absolutePath}" },
                        { "Extraction impossible : ${it.message ?: "erreur inconnue"}" }
                    )
                    password = ""
                }
            }) { Text("Extraire") }
        },
        dismissButton = { TextButton(enabled = !extracting, onClick = { showExtractDialog = false; password = "" }) { Text("Annuler") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilePreviewScreen(file: File, onClose: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(formatSize(file.length()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Retour") } },
                actions = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Fermer") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).navigationBarsPadding().padding(8.dp)) {
            when (file.extension.lowercase()) {
                "jpg", "jpeg", "png", "webp", "gif", "bmp" -> AsyncImage(model = file, contentDescription = file.name, modifier = Modifier.fillMaxSize())
                "pdf" -> PdfFilePreview(file)
                "mp4", "mkv", "webm", "mov", "m4v" -> VideoFilePreview(file)
                "mp3", "wav", "ogg", "flac", "m4a" -> AudioFilePreview(file)
                "jks", "keystore", "jceks", "p12", "pfx", "bks" -> KeystoreFilePreview(file)
                "pem", "crt", "cer", "der" -> CertificateFilePreview(file)
                "zip", "7z", "tar", "tgz", "gz", "bz2", "xz", "tbz", "tbz2", "txz" -> ArchivePreview(file)
                else -> {
                    val context = LocalContext.current
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.InsertDriveFile, null, modifier = Modifier.size(56.dp))
                        Text("Aucun aperçu intégré pour ce format.")
                        Text("${file.name} • ${formatSize(file.length())}", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { openWithExternal(context, file) }) { Text("Ouvrir avec une autre application") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfFilePreview(file: File) {
    var page by remember(file) { mutableIntStateOf(0) }
    val bitmapState = produceState<Bitmap?>(null, file, page) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount == 0) null else renderer.openPage(page.coerceIn(0, renderer.pageCount - 1)).use { pdfPage ->
                        val width = 1200
                        val height = (width.toFloat() * pdfPage.height / pdfPage.width).toInt().coerceAtLeast(1)
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap -> pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                    }
                }
            }.getOrNull()
        }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = { page = (page - 1).coerceAtLeast(0) }, enabled = page > 0) { Text("Précédent") }; Text("Page ${page + 1}", style = MaterialTheme.typography.labelMedium); TextButton(onClick = { page++ }) { Text("Suivante") } }
        bitmapState.value?.let { Image(it.asImageBitmap(), null, modifier = Modifier.fillMaxSize()) } ?: Text("PDF illisible ou protégé par mot de passe", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VideoFilePreview(file: File) {
    val context = LocalContext.current
    val player = remember(file) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(Uri.fromFile(file))); prepare(); playWhenReady = false } }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun AudioFilePreview(file: File) {
    val context = LocalContext.current
    val player = remember(file) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(Uri.fromFile(file))); prepare() } }
    DisposableEffect(player) { onDispose { player.release() } }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.AudioFile, null, modifier = Modifier.size(72.dp))
        Text(file.name, fontWeight = FontWeight.SemiBold)
        Row { TextButton(onClick = { player.playWhenReady = !player.playWhenReady }) { Text("Lecture / Pause") }; TextButton(onClick = { player.seekTo(0) }) { Text("Recommencer") } }
    }
}



@Composable
private fun RecentFilesPage(paths: List<String>, onOpen: (File) -> Unit, onInfo: (File) -> Unit, onClear: () -> Unit, onBack: () -> Unit) {
    val existing = remember(paths) { paths.map { File(it) }.filter { it.exists() && it.isFile }.distinctBy { it.absolutePath } }
    ZernexPageScaffold(
        title = "Fichiers récents", subtitle = "${existing.size} éléments", onBack = onBack,
        actions = { TextButton(onClick = onClear, enabled = existing.isNotEmpty()) { Text("Effacer") } }
    ) {
        if (existing.isEmpty()) ZernexEmptyState("Aucun fichier récent", "Les fichiers ouverts apparaîtront ici.") { Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(42.dp)) }
        else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(existing, key = { it.absolutePath }) { item ->
                ListItem(
                    headlineContent = { Text(item.name, maxLines = 1) },
                    supportingContent = { Text("${formatSize(item.length())} • ${item.parent}", maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingContent = { FileTypeIcon(item, 38.dp) },
                    trailingContent = { IconButton(onClick = { onInfo(item) }) { Icon(Icons.Default.Info, "Informations") } },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(item) }
                )
            }
        }
    }
}

@Composable
private fun FavoritesPage(paths: List<String>, onOpen: (String) -> Unit, onRemove: (String) -> Unit, onBack: () -> Unit) {
    val valid = remember(paths) { paths.filter { File(it).isDirectory } }
    ZernexPageScaffold(title = "Dossiers favoris", subtitle = "${valid.size} emplacements", onBack = onBack) {
        if (valid.isEmpty()) ZernexEmptyState("Aucun favori", "Ajoute un dossier depuis le menu de navigation.") { Icon(Icons.Default.Bookmarks, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(42.dp)) }
        else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(valid, key = { it }) { path ->
                ListItem(
                    headlineContent = { Text(File(path).name.ifBlank { "Stockage" }, maxLines = 1) },
                    supportingContent = { Text(path, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingContent = { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { IconButton(onClick = { onRemove(path) }) { Icon(Icons.Default.BookmarkRemove, "Retirer des favoris") } },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(path) }
                )
            }
        }
    }
}

@Composable
private fun TrashPage(entries: List<TrashEntry>, onRestore: (TrashEntry) -> Unit, onPurge: () -> Unit, onBack: () -> Unit) {
    var askPurge by remember { mutableStateOf(false) }
    ZernexPageScaffold(
        title = "Corbeille", subtitle = "${entries.size} éléments", onBack = onBack,
        actions = { TextButton(enabled = entries.isNotEmpty(), onClick = { askPurge = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Vider") } }
    ) {
        if (entries.isEmpty()) ZernexEmptyState("Corbeille vide", "Les éléments supprimés pourront être restaurés ici.") { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(42.dp)) }
        else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(entries, key = { it.file.absolutePath }) { entry ->
                ListItem(
                    headlineContent = { Text(entry.file.name, maxLines = 1) },
                    supportingContent = { Text("Origine : ${entry.originalPath}", maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingContent = { Icon(if (entry.file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, null, tint = MaterialTheme.colorScheme.secondary) },
                    trailingContent = { IconButton(onClick = { onRestore(entry) }) { Icon(Icons.Default.Restore, "Restaurer", tint = MaterialTheme.colorScheme.primary) } }
                )
            }
        }
    }
    if (askPurge) AlertDialog(onDismissRequest = { askPurge = false }, title = { Text("Vider définitivement la corbeille ?") }, text = { Text("${entries.size} élément(s) seront supprimés sans possibilité de restauration.") }, confirmButton = { TextButton(onClick = { askPurge = false; onPurge() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Supprimer définitivement") } }, dismissButton = { TextButton(onClick = { askPurge = false }) { Text("Annuler") } })
}

@Composable
private fun DuplicatesPage(groups: List<List<File>>, onBack: () -> Unit) {
    val count = groups.sumOf { (it.size - 1).coerceAtLeast(0) }
    ZernexPageScaffold(title = "Fichiers en double", subtitle = "$count copie(s) potentielle(s)", onBack = onBack) {
        if (groups.isEmpty()) ZernexEmptyState("Aucun doublon trouvé", "Les groupes sont calculés à partir du contenu des fichiers.") { Icon(Icons.Default.ContentCopy, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(42.dp)) }
        else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(groups.size) { index ->
                val group = groups[index]
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Même contenu • ${formatSize(group.first().length())}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        group.forEach { file -> Text(file.absolutePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        Text("Vérifie les emplacements avant de supprimer une copie.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StoragePage(summary: StorageSummary, onBack: () -> Unit) {
    val ratio = (summary.used.toFloat() / summary.total.coerceAtLeast(1L)).coerceIn(0f, 1f)
    ZernexPageScaffold(title = "Espace de stockage", subtitle = "Stockage partagé", onBack = onBack) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Utilisé", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatSize(summary.used), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                DetailLine("Capacité totale", formatSize(summary.total))
                DetailLine("Disponible", formatSize(summary.free))
            }
        }
    }
}

@Composable
private fun PermissionsPage(granted: Boolean, onAuthorize: () -> Unit, onBack: () -> Unit) {
    ZernexPageScaffold(title = "Accès aux fichiers", subtitle = "Autorisations Android", onBack = onBack) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (granted) Icons.Default.CheckCircle else Icons.Default.Lock, null, tint = if (granted) ZernexPalette.green else MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    Text(if (granted) "Accès autorisé" else "Accès à autoriser", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text("L'autorisation permet à ZERNEX Files d'afficher et d'organiser les fichiers du stockage partagé. Les espaces privés des autres applications restent protégés par Android.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!granted) Button(onClick = onAuthorize, modifier = Modifier.fillMaxWidth()) { Text("Autoriser l'accès") }
            }
        }
    }
}
