package com.zernex.files

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.security.MessageDigest

private enum class SafSort { NAME, SIZE, DATE, TYPE }
private enum class SafLayout { LIST, GRID }

/** Shared three-accent semantic palette for local and SAF file entries. */
private fun safAccent(entry: SafEntry): Color =
    zernexAccentFor(entry.name, entry.isDirectory)


private data class SafEntry(
    val document: DocumentFile,
    val relativePath: String,
    val name: String = document.name ?: "Sans nom",
    val isDirectory: Boolean = document.isDirectory,
    val size: Long = document.length(),
    val modified: Long = document.lastModified(),
    val mime: String? = document.type
) {
    val key: String get() = document.uri.toString()
}

@Composable
fun SafAccessScreen(onPickFolder: () -> Unit, onExit: () -> Unit, showBack: Boolean = true) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(62.dp))
            Text("Ouvrir un emplacement", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Sélectionne le dossier à parcourir.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onPickFolder, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FolderOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("Sélectionner un dossier")
            }
            Text(
                "Accès mémorisé sur cet appareil.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (showBack) TextButton(onClick = onExit) { Text("Retour") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SafFileManagerScreen(
    treeUri: Uri,
    onPickAnotherFolder: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("zernex_browser", Context.MODE_PRIVATE) }
    val treeKey = treeUri.toString().hashCode().toString()
    val stackPrefKey = "saf_stack_$treeKey"
    val favoritesPrefKey = "saf_favorites_$treeKey"
    val rootDocument = remember(treeUri) { runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull() }
    val savedParts = remember(treeUri) {
        prefs.getString(stackPrefKey, "").orEmpty().split('/').filter { it.isNotEmpty() }
    }
    var stackParts by remember(treeUri) { mutableStateOf(savedParts) }
    var refresh by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var searchSubfolders by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(prefs.getBoolean("show_hidden", false)) }
    var category by remember { mutableStateOf("Tous") }
    var sort by remember { mutableStateOf(runCatching { SafSort.valueOf(prefs.getString("saf_sort", "NAME") ?: "NAME") }.getOrDefault(SafSort.NAME)) }
    var ascending by remember { mutableStateOf(prefs.getBoolean("saf_ascending", true)) }
    var layout by remember { mutableStateOf(if (prefs.getString("saf_layout", "list") == "grid") SafLayout.GRID else SafLayout.LIST) }
    var entries by remember { mutableStateOf<List<SafEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var readError by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val selectedKeys = remember { mutableStateListOf<String>() }
    var clipboard by remember { mutableStateOf<List<SafEntry>>(emptyList()) }
    var clipboardIsMove by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf("") }
    var inputName by remember { mutableStateOf("") }
    var bulkRenamePrefix by remember { mutableStateOf("") }
    var bulkRenameSuffix by remember { mutableStateOf("") }
    var activeEntry by remember { mutableStateOf<SafEntry?>(null) }
    val trashPrefKey = "saf_trash_records_$treeKey"
    var trashRecords by remember(treeUri) { mutableStateOf(loadSafTrashRecords(prefs, trashPrefKey)) }
    var editorEntry by remember { mutableStateOf<SafEntry?>(null) }
    var editorText by remember { mutableStateOf("") }
    var editorReadOnly by remember { mutableStateOf(false) }
    var editorLoading by remember { mutableStateOf(false) }
    var editorDirty by remember { mutableStateOf(false) }
    var previewEntry by remember { mutableStateOf<SafEntry?>(null) }
    var activeTrashRecord by remember { mutableStateOf<SafTrashRecord?>(null) }
    var favoritePaths by remember(treeUri) {
        mutableStateOf(prefs.getStringSet(favoritesPrefKey, emptySet())?.toList() ?: emptyList())
    }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = busy || dialog.isNotEmpty() || editorEntry != null || previewEntry != null || selectedKeys.isNotEmpty() || stackParts.isNotEmpty()) {
        when {
            busy -> message = "Opération en cours : attends sa fin avant de quitter ce dossier."
            dialog == "editor_unsaved" -> dialog = ""
            dialog == "purge" -> { dialog = "trashbin"; activeTrashRecord = null }
            editorEntry != null -> if (editorDirty) dialog = "editor_unsaved" else editorEntry = null
            previewEntry != null -> previewEntry = null
            dialog.isNotEmpty() -> dialog = ""
            selectedKeys.isNotEmpty() -> selectedKeys.clear()
            stackParts.isNotEmpty() -> stackParts = stackParts.dropLast(1)
            else -> message = "Tu es déjà à la racine du dossier sélectionné."
        }
    }

    val navigationDocuments = remember(rootDocument, stackParts, refresh) {
        val result = mutableListOf<DocumentFile>()
        rootDocument?.let { root ->
            result.add(root)
            var cursor = root
            for (part in stackParts) {
                val next = runCatching { cursor.findFile(part) }.getOrNull()
                if (next == null || !next.isDirectory) break
                result.add(next)
                cursor = next
            }
        }
        result
    }
    val currentDirectory = navigationDocuments.lastOrNull()
    val currentRelativePath = stackParts.take((navigationDocuments.size - 1).coerceAtLeast(0)).joinToString("/")

    LaunchedEffect(stackParts, navigationDocuments.size) {
        if (navigationDocuments.size - 1 < stackParts.size) {
            stackParts = stackParts.take((navigationDocuments.size - 1).coerceAtLeast(0))
        }
    }
    LaunchedEffect(stackParts) {
        prefs.edit().putString(stackPrefKey, stackParts.joinToString("/")).apply()
        selectedKeys.clear()
    }
    LaunchedEffect(query, searchSubfolders, showHidden, category) { selectedKeys.clear() }
    LaunchedEffect(currentDirectory?.uri, refresh, query, searchSubfolders, showHidden, category, sort, ascending) {
        loading = true
        readError = null
        val base = currentDirectory
        val (result, failureMessage) = withContext(Dispatchers.IO) {
            var failure: String? = null
            if (base == null || !runCatching { base.exists() && base.isDirectory }.getOrDefault(false)) {
                emptyList<SafEntry>() to "Le dossier sélectionné n'est plus disponible. Sélectionne-le à nouveau."
            } else {
                val found = ArrayList<SafEntry>()
                if (!searchSubfolders) {
                    val children = runCatching { base.listFiles().toList() }.getOrElse {
                        failure = "Lecture refusée : ${it.message ?: "dossier inaccessible"}"
                        emptyList()
                    }
                    children.forEach { document ->
                        val name = document.name ?: return@forEach
                        if (name == ".ZERNEX_Corbeille") return@forEach
                        if ((showHidden || !name.startsWith('.')) && (query.isBlank() || name.contains(query.trim(), ignoreCase = true))) {
                            val entry = SafEntry(document, listOf(currentRelativePath, name).filter { it.isNotBlank() }.joinToString("/"))
                            if (safCategoryMatches(entry, category)) found.add(entry)
                        }
                    }
                } else {
                    val stack = ArrayDeque<Pair<DocumentFile, String>>()
                    stack.add(base to currentRelativePath)
                    var visited = 0
                    while (stack.isNotEmpty() && visited < 10000) {
                        val (directory, parentPath) = stack.removeLast()
                        val children = runCatching { directory.listFiles().toList() }.getOrDefault(emptyList())
                        for (document in children) {
                            visited++
                            if (visited > 10000) break
                            val name = document.name ?: continue
                            if (name == ".ZERNEX_Corbeille") continue
                            if (!showHidden && name.startsWith('.')) continue
                            val relative = listOf(parentPath, name).filter { it.isNotBlank() }.joinToString("/")
                            val entry = SafEntry(document, relative)
                            if ((query.isBlank() || name.contains(query.trim(), ignoreCase = true)) && safCategoryMatches(entry, category)) {
                                found.add(entry)
                            }
                            if (document.isDirectory) stack.add(document to relative)
                        }
                    }
                }
                found.sortWith { first, second ->
                    if (first.isDirectory != second.isDirectory) {
                        if (first.isDirectory) -1 else 1
                    } else {
                        val compared = when (sort) {
                            SafSort.NAME -> first.name.lowercase(Locale.ROOT).compareTo(second.name.lowercase(Locale.ROOT))
                            SafSort.SIZE -> first.size.compareTo(second.size)
                            SafSort.DATE -> first.modified.compareTo(second.modified)
                            SafSort.TYPE -> (first.mime ?: first.name.substringAfterLast('.', "")).compareTo(second.mime ?: second.name.substringAfterLast('.', ""), ignoreCase = true)
                        }
                        if (ascending) compared else -compared
                    }
                }
                found to failure
            }
        }
        entries = result
        readError = failureMessage
        loading = false
    }

    fun refreshNow() { refresh++ }
    fun navigateTo(relativePath: String) {
        val parts = relativePath.split('/').filter { it.isNotBlank() }
        var cursor = rootDocument
        val resolved = mutableListOf<String>()
        for (part in parts) {
            val next = cursor?.findFile(part)
            if (next == null || !next.isDirectory) break
            resolved.add(part)
            cursor = next
        }
        stackParts = resolved
        query = ""
        searchOpen = false
        refreshNow()
    }
    fun openDocument(entry: SafEntry) {
        if (entry.isDirectory) {
            navigateTo(entry.relativePath)
            return
        }
        when {
            isSafImageFile(entry.name) -> previewEntry = entry
            isSafZipFile(entry.name) -> { activeEntry = entry; dialog = "extract" }
            isSafTextFile(entry.name) -> {
                editorEntry = entry
                editorLoading = true
                scope.launch {
                    val content = withContext(Dispatchers.IO) { runCatching { readSafText(context, entry.document) } }
                    if (editorEntry?.key == entry.key) {
                        editorLoading = false
                        content.onSuccess {
                            editorText = it
                            editorDirty = false
                            editorReadOnly = !entry.document.canWrite()
                        }.onFailure {
                            editorEntry = null
                            message = "Aperçu texte indisponible : ${it.message ?: "fichier trop volumineux ou lecture refusée"}"
                            openSafExternally(context, entry)
                        }
                    }
                }
            }
            else -> openSafExternally(context, entry)
        }
    }
    fun persistFavorites() {
        prefs.edit().putStringSet(favoritesPrefKey, favoritePaths.toSet()).apply()
    }
    fun createFolderOrFile(isFolder: Boolean) {
        val name = inputName.trim()
        if (!isValidSafLeafName(name)) { message = "Nom invalide (255 octets maximum, sans séparateur)"; return }
        val finalName = if (!isFolder && !name.endsWith(".txt", true)) "$name.txt" else name
        val parent = currentDirectory
        dialog = ""
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val targetParent = parent ?: error("Dossier indisponible")
                    if (!targetParent.canWrite()) error("Écriture refusée dans le dossier courant")
                    if (findSafChildByName(targetParent, finalName) != null) error("Un élément porte déjà ce nom (majuscules/minuscules ignorées)")
                    if (isFolder) targetParent.createDirectory(finalName) ?: error("Création impossible")
                    else targetParent.createFile("text/plain", finalName) ?: error("Création impossible")
                }
            }
            busy = false
            message = result.fold({ if (isFolder) "Dossier créé" else "Fichier texte créé" }, { "Échec : ${it.message ?: "fournisseur Android indisponible"}" })
            refreshNow()
        }
    }

    if (editorEntry != null) {
        val editing = editorEntry!!
        SafTextEditorPage(
            entry = editing,
            text = editorText,
            loading = editorLoading,
            readOnly = editorReadOnly,
            saving = busy,
            onTextChange = { editorText = it; editorDirty = true },
            onBack = { if (editorDirty) dialog = "editor_unsaved" else editorEntry = null },
            onSave = {
                busy = true
                scope.launch {
                    val saved = withContext(Dispatchers.IO) { runCatching { writeSafText(context, editing.document, editorText) } }
                    busy = false
                    saved.onSuccess {
                        editorDirty = false
                        message = "Fichier enregistré"
                        editorEntry = null
                        refreshNow()
                    }.onFailure { message = "Enregistrement impossible : ${it.message ?: "écriture refusée"}" }
                }
            }
        )
        if (dialog == "editor_unsaved") {
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("Modifications non enregistrées") },
                text = { Text("Fermer l’éditeur sans enregistrer les changements ?") },
                confirmButton = { TextButton(onClick = { dialog = ""; editorEntry = null; editorDirty = false }) { Text("Fermer sans enregistrer") } },
                dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Continuer l’édition") } }
            )
        }
        return
    }
    if (previewEntry != null) {
        SafPreviewPage(entry = previewEntry!!, onBack = { previewEntry = null })
        return
    }
    if (dialog == "properties" && activeEntry != null) {
        SafPropertiesPage(entry = activeEntry!!, context = context, onBack = { dialog = "" })
        return
    }
    if (dialog == "purge" && activeTrashRecord != null) {
        SafPurgeConfirmation(
            record = activeTrashRecord!!,
            onDismiss = { dialog = "trashbin"; activeTrashRecord = null },
            onConfirm = {
                val record = activeTrashRecord!!
                busy = true
                scope.launch {
                    val deleted = withContext(Dispatchers.IO) { runCatching { deleteSafTrash(rootDocument, record) }.getOrDefault(false) }
                    busy = false
                    if (deleted) {
                        trashRecords = trashRecords - record
                        saveSafTrashRecords(prefs, trashPrefKey, trashRecords)
                        message = "Élément supprimé définitivement"
                    } else message = "Suppression impossible"
                    activeTrashRecord = null
                    dialog = "trashbin"
                }
            }
        )
        return
    }
    if (dialog == "favorites") {
        SafFavoritesPage(
            paths = favoritePaths,
            onOpen = { path -> dialog = ""; navigateTo(path) },
            onRemove = { path -> favoritePaths = favoritePaths - path; persistFavorites() },
            onBack = { dialog = "" }
        )
        return
    }
    if (dialog == "trashbin") {
        SafTrashPage(
            records = trashRecords,
            busy = busy,
            onRestore = { record ->
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { restoreSafTrash(context, rootDocument, record) } }
                    busy = false
                    result.onSuccess { restoredName ->
                        trashRecords = trashRecords - record
                        saveSafTrashRecords(prefs, trashPrefKey, trashRecords)
                        message = "Restauré : $restoredName"
                        refreshNow()
                    }.onFailure { message = "Restauration impossible : ${it.message ?: "erreur inconnue"}" }
                }
            },
            onDelete = { record -> activeTrashRecord = record; dialog = "purge" },
            onBack = { dialog = "" }
        )
        return
    }
    if (dialog == "settings") {
        SafSettingsPage(
            showHidden = showHidden,
            layout = layout,
            sort = sort,
            ascending = ascending,
            onShowHidden = { showHidden = it; prefs.edit().putBoolean("show_hidden", it).apply(); refreshNow() },
            onLayout = { layout = it; prefs.edit().putString("saf_layout", if (it == SafLayout.GRID) "grid" else "list").apply() },
            onSort = { sort = it; prefs.edit().putString("saf_sort", it.name).apply() },
            onAscending = { ascending = it; prefs.edit().putBoolean("saf_ascending", it).apply() },
            onBack = { dialog = "" }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ZernexBrandMark()
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("ZERNEX Files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    Text("/", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.combinedClickable(enabled = !busy, onClick = { navigateTo("") }, onLongClick = { navigateTo("") }))
                    navigationDocuments.drop(1).forEachIndexed { index, document ->
                        Text(" / ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        Text(
                            document.name ?: "Dossier",
                            color = if (index == navigationDocuments.lastIndex - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            modifier = Modifier.combinedClickable(
                                enabled = !busy,
                                onClick = { navigateTo(stackParts.take(index + 1).joinToString("/")) },
                                onLongClick = { navigateTo(stackParts.take(index + 1).joinToString("/")) }
                            )
                        )
                    }
                }
            }
            IconButton(enabled = !busy, onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) { Icon(Icons.Default.Search, "Rechercher") }
            IconButton(enabled = !busy, onClick = onPickAnotherFolder) { Icon(Icons.Default.FolderSpecial, "Changer de dossier") }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("${entries.size} élément(s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(enabled = !busy, onClick = { layout = if (layout == SafLayout.LIST) SafLayout.GRID else SafLayout.LIST; prefs.edit().putString("saf_layout", if (layout == SafLayout.LIST) "grid" else "list").apply() }) {
                Icon(if (layout == SafLayout.LIST) Icons.Default.GridView else Icons.Default.ViewList, "Changer l'affichage")
            }
            var sortMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(enabled = !busy, onClick = { sortMenu = true }) { Icon(Icons.Default.Sort, "Trier") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SafSort.entries.forEach { option ->
                        DropdownMenuItem(
                            enabled = !busy,
                            text = { Text(when (option) { SafSort.NAME -> "Nom"; SafSort.SIZE -> "Taille"; SafSort.DATE -> "Date"; SafSort.TYPE -> "Type" }) },
                            onClick = { sort = option; prefs.edit().putString("saf_sort", option.name).apply(); sortMenu = false }
                        )
                    }
                    DropdownMenuItem(enabled = !busy, text = { Text(if (ascending) "Ordre décroissant" else "Ordre croissant") }, onClick = { ascending = !ascending; prefs.edit().putBoolean("saf_ascending", ascending).apply(); sortMenu = false })
                }
            }
            var moreMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { if (!busy) moreMenu = true }, enabled = !busy) { Icon(Icons.Default.MoreVert, "Plus") }
                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    DropdownMenuItem(enabled = !busy, text = { Text(if (showHidden) "Masquer les fichiers cachés" else "Afficher les fichiers cachés") }, leadingIcon = { Icon(Icons.Default.Visibility, null) }, onClick = { showHidden = !showHidden; prefs.edit().putBoolean("show_hidden", showHidden).apply(); moreMenu = false })
                    DropdownMenuItem(enabled = !busy, text = { Text(if (searchSubfolders) "Recherche dans le dossier actuel" else "Inclure les sous-dossiers") }, leadingIcon = { Icon(Icons.Default.ManageSearch, null) }, onClick = { searchSubfolders = !searchSubfolders; moreMenu = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Ajouter ce dossier aux favoris") }, leadingIcon = { Icon(Icons.Default.Star, null) }, onClick = { favoritePaths = (favoritePaths + currentRelativePath).distinct(); persistFavorites(); message = "Favori ajouté"; moreMenu = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Dossiers favoris (${favoritePaths.size})") }, leadingIcon = { Icon(Icons.Default.Bookmarks, null) }, onClick = { dialog = "favorites"; moreMenu = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Corbeille (${trashRecords.size})") }, leadingIcon = { Icon(Icons.Default.DeleteSweep, null) }, onClick = { dialog = "trashbin"; moreMenu = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Paramètres") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { dialog = "settings"; moreMenu = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Nouveau dossier") }, leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) }, onClick = { inputName = ""; dialog = "mkdir"; moreMenu = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Nouveau fichier texte") }, leadingIcon = { Icon(Icons.Default.NoteAdd, null) }, onClick = { inputName = ""; dialog = "newtxt"; moreMenu = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Changer de dossier") }, leadingIcon = { Icon(Icons.Default.FolderSpecial, null) }, onClick = { onPickAnotherFolder(); moreMenu = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Actualiser") }, leadingIcon = { Icon(Icons.Default.Refresh, null) }, onClick = { refreshNow(); moreMenu = false })
                    DropdownMenuItem(enabled = !busy, text = { Text("Quitter le mode SAF") }, leadingIcon = { Icon(Icons.Default.ArrowBack, null) }, onClick = { onExit(); moreMenu = false })
                }
            }
        }
        ZernexCategoryRail(selected = category, onSelected = { category = it })
        if (searchOpen) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Nom de fichier ou de dossier…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, null) } }
            )
            Text(
                if (searchSubfolders) "Recherche dans les sous-dossiers activée (limite de 10 000 éléments par recherche)." else "Recherche dans le dossier courant.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        message?.let { msg ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(msg, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { message = null }) { Icon(Icons.Default.Close, null) }
            }
        }
        if (clipboard.isNotEmpty()) {
            Text("Presse-papiers actif : colle les éléments ou efface-le avant de renommer, supprimer ou recompresser des fichiers.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${clipboard.size} élément(s) à ${if (clipboardIsMove) "déplacer" else "copier"}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !busy, onClick = {
                    val sources = clipboard.toList()
                    val move = clipboardIsMove
                    val target = currentDirectory
                    if (target == null) { message = "Dossier de destination indisponible" }
                    else {
                        busy = true
                        scope.launch {
                            val (completedKeys, failureMessage) = withContext(Dispatchers.IO) {
                                val completed = ArrayList<String>()
                                var failure: String? = null
                                val budget = SafCopyBudget()
                                for (source in sources) {
                                    try {
                                        val sourceParent = source.relativePath.substringBeforeLast('/', "")
                                        if (source.isDirectory && (currentRelativePath == source.relativePath || currentRelativePath.startsWith(source.relativePath + "/"))) {
                                            error("Impossible de copier ou déplacer un dossier dans lui-même ou dans l'un de ses sous-dossiers")
                                        }
                                        if (!(move && sourceParent == currentRelativePath)) {
                                            copySafDocument(context, source.document, target, budget)
                                            if (move && !deleteSafTree(source.document)) {
                                                completed.add(source.key)
                                                error("Copie effectuée, mais la source n'a pas pu être supprimée : ${source.name}. Vérifie les deux emplacements.")
                                            }
                                        }
                                        completed.add(source.key)
                                    } catch (error: Exception) {
                                        failure = error.message ?: "Erreur de copie ou déplacement"
                                        break
                                    }
                                }
                                completed to failure
                            }
                            busy = false
                            clipboard = sources.filterNot { it.key in completedKeys }
                            message = if (failureMessage == null) "${completedKeys.size} élément(s) traité(s)" else "${completedKeys.size} traité(s), reste à faire : ${failureMessage}"
                            refreshNow()
                        }
                    }
                }) { Text("Coller ici") }
                TextButton(enabled = !busy, onClick = { clipboard = emptyList() }) { Text("Effacer") }
            }
        }
        if (busy || loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (readError != null) Text(readError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (entries.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (loading) Icons.Default.FolderOpen else Icons.Default.FolderOff, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (loading) "Lecture des fichiers…" else if (query.isNotBlank()) "Aucun résultat" else "Ce dossier est vide ou inaccessible", fontWeight = FontWeight.SemiBold)
                    if (!loading) Text("Vérifie l'autorisation du dossier ou sélectionne un autre emplacement.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else if (layout == SafLayout.LIST) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(top = 6.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(entries, key = { it.key }) { entry ->
                    SafFileRow(
                        entry = entry,
                        selected = entry.key in selectedKeys,
                        enabled = !busy,
                        onClick = {
                            if (busy) Unit
                            else if (selectedKeys.isNotEmpty()) { if (entry.key in selectedKeys) selectedKeys.remove(entry.key) else selectedKeys.add(entry.key) }
                            else openDocument(entry)
                        },
                        onLongClick = { if (!busy) { if (entry.key in selectedKeys) selectedKeys.remove(entry.key) else selectedKeys.add(entry.key) } }
                    )
                }
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Adaptive(148.dp), modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(top = 6.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                gridItems(entries, key = { it.key }) { entry ->
                    SafFileCard(
                        entry = entry,
                        selected = entry.key in selectedKeys,
                        enabled = !busy,
                        onClick = {
                            if (busy) Unit
                            else if (selectedKeys.isNotEmpty()) { if (entry.key in selectedKeys) selectedKeys.remove(entry.key) else selectedKeys.add(entry.key) }
                            else openDocument(entry)
                        },
                        onLongClick = { if (!busy) { if (entry.key in selectedKeys) selectedKeys.remove(entry.key) else selectedKeys.add(entry.key) } }
                    )
                }
            }
        }
        if (selectedKeys.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${collapseSafSelection(entries.filter { it.key in selectedKeys }).size} sélectionné(s)", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        TextButton(enabled = !busy, onClick = { selectedKeys.clear() }) { Text("Fermer") }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = !busy && clipboard.isEmpty(), onClick = { val picked = collapseSafSelection(entries.filter { it.key in selectedKeys }); clipboard = picked; clipboardIsMove = false; selectedKeys.clear(); message = "${picked.size} élément(s) prêt(s) à copier" }) { Icon(Icons.Default.ContentCopy, null); Text("Copier") }
                        TextButton(enabled = !busy && clipboard.isEmpty(), onClick = { val picked = collapseSafSelection(entries.filter { it.key in selectedKeys }); clipboard = picked; clipboardIsMove = true; selectedKeys.clear(); message = "${picked.size} élément(s) prêt(s) à déplacer" }) { Icon(Icons.Default.ContentCut, null); Text("Couper") }
                        TextButton(enabled = !busy && clipboard.isEmpty(), onClick = { val picked = collapseSafSelection(entries.filter { it.key in selectedKeys }); if (picked.size == 1) { activeEntry = picked.first(); inputName = picked.first().name; dialog = "rename" } else { bulkRenamePrefix = ""; bulkRenameSuffix = ""; dialog = "bulk_rename" } }) { Icon(Icons.Default.DriveFileRenameOutline, null); Text("Renommer") }
                        TextButton(enabled = !busy && clipboard.isEmpty(), onClick = { dialog = "trash" }) { Icon(Icons.Default.DeleteOutline, null); Text("Corbeille") }
                        TextButton(enabled = !busy, onClick = { val picked = collapseSafSelection(entries.filter { it.key in selectedKeys }); shareSafDocuments(context, picked) }) { Icon(Icons.Default.Share, null); Text("Partager") }
                        if (collapseSafSelection(entries.filter { it.key in selectedKeys }).size == 1) TextButton(enabled = !busy, onClick = { activeEntry = collapseSafSelection(entries.filter { it.key in selectedKeys }).first(); dialog = "properties" }) { Icon(Icons.Default.Info, null); Text("Détails") }
                        if (collapseSafSelection(entries.filter { it.key in selectedKeys }).size == 1 && isSafZipFile(collapseSafSelection(entries.filter { it.key in selectedKeys }).first().name)) TextButton(enabled = !busy && clipboard.isEmpty(), onClick = { activeEntry = collapseSafSelection(entries.filter { it.key in selectedKeys }).first(); dialog = "extract" }) { Icon(Icons.Default.Unarchive, null); Text("Extraire") }
                        TextButton(enabled = !busy && clipboard.isEmpty(), onClick = {
                            val picked = collapseSafSelection(entries.filter { it.key in selectedKeys })
                            val parent = currentDirectory
                            if (parent == null) message = "Dossier de destination indisponible"
                            else {
                                busy = true; selectedKeys.clear()
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { runCatching { createSafZip(context, picked.map { it.document }, parent) } }
                                    busy = false
                                    message = result.fold({ "Archive créée : ${it.name ?: "Archive.zip"}" }, { "Compression impossible : ${it.message ?: "erreur inconnue"}" })
                                    refreshNow()
                                }
                            }
                        }) { Icon(Icons.Default.FolderZip, null); Text("ZIP") }
                    }
                }
            }
        }
        if (selectedKeys.isEmpty()) {
            ZernexSafBottomNavigation(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                onFiles = { dialog = "" },
                onFavorites = { dialog = "favorites" },
                onTrash = { dialog = "trashbin" },
                onSettings = { dialog = "settings" }
            )
        }
    }


    if (dialog == "mkdir" || dialog == "newtxt" || dialog == "rename") {
        AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text(when (dialog) { "mkdir" -> "Créer un dossier"; "newtxt" -> "Créer un fichier texte"; else -> "Renommer" }) },
            text = { OutlinedTextField(value = inputName, onValueChange = { inputName = it }, label = { Text("Nom") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (dialog == "mkdir") createFolderOrFile(true)
                    else if (dialog == "newtxt") createFolderOrFile(false)
                    else {
                        val clean = inputName.trim()
                        val entry = activeEntry
                        when {
                            !isValidSafLeafName(clean) -> message = "Nom invalide (255 octets maximum, sans séparateur)"
                            entry == null -> message = "Élément à renommer indisponible"
                            clean == entry.name -> message = "Le nouveau nom est identique à l'ancien"
                            else -> {
                                val parentPath = entry.relativePath.substringBeforeLast('/', "")
                                dialog = ""
                                busy = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            val root = rootDocument ?: error("Racine SAF indisponible")
                                            val parent = findSafDirectory(root, parentPath) ?: error("Dossier parent indisponible")
                                            if (!entry.document.canWrite() || !parent.canWrite()) error("Écriture refusée par Android")
                                            val existing = findSafChildByName(parent, clean)
                                            if (existing != null && existing.uri != entry.document.uri) error("Un élément porte déjà ce nom (majuscules/minuscules ignorées)")
                                            if (!entry.document.renameTo(clean)) error("Le fournisseur Android a refusé le renommage")
                                        }
                                    }
                                    busy = false
                                    message = result.fold({ "Élément renommé" }, { "Renommage impossible : ${it.message ?: "erreur inconnue"}" })
                                    activeEntry = null
                                    selectedKeys.clear()
                                    refreshNow()
                                }
                            }
                        }
                    }
                }) { Text("Valider") }
            },
            dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Annuler") } }
        )
    }
    if (dialog == "bulk_rename") {
        val itemsToRename = collapseSafSelection(entries.filter { it.key in selectedKeys })
        AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text("Renommer la sélection") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ajoute un début et/ou une fin au nom. Les extensions des fichiers sont conservées.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = bulkRenamePrefix, onValueChange = { bulkRenamePrefix = it }, label = { Text("Début du nom") }, singleLine = true)
                    OutlinedTextField(value = bulkRenameSuffix, onValueChange = { bulkRenameSuffix = it }, label = { Text("Fin du nom") }, singleLine = true)
                    Text("${itemsToRename.size} élément(s). Un dossier sélectionné inclut son contenu ; ses enfants ne seront pas renommés séparément.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tout conflit est vérifié avant le début du renommage.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && clipboard.isEmpty() && itemsToRename.isNotEmpty() && (bulkRenamePrefix.isNotBlank() || bulkRenameSuffix.isNotBlank()), onClick = {
                    val pending = itemsToRename.toList()
                    val prefix = bulkRenamePrefix
                    val suffix = bulkRenameSuffix
                    dialog = ""
                    selectedKeys.clear()
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { renameSafSelection(pending, rootDocument, prefix, suffix) }
                        }
                        busy = false
                        message = result.getOrElse { "Renommage impossible : ${it.message ?: "fournisseur Android indisponible"}" }
                        refreshNow()
                    }
                }) { Text("Renommer") }
            },
            dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Annuler") } }
        )
    }
    if (dialog == "trash") {
        val trashing = collapseSafSelection(entries.filter { it.key in selectedKeys })
        AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text("Déplacer vers la corbeille ?") },
            text = {
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    Text("Les éléments seront déplacés dans un dossier masqué .ZERNEX_Corbeille, dans l'emplacement sélectionné. Vous pourrez les restaurer depuis la corbeille ZERNEX.")
                    trashing.forEach { Text("• ${it.name}", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = ""
                    busy = true
                    scope.launch {
                        val (added, errors) = withContext(Dispatchers.IO) {
                            val moved = mutableListOf<SafTrashOutcome>()
                            val failed = mutableListOf<String>()
                            trashing.forEach { item ->
                                runCatching { trashSafEntry(context, rootDocument, item) }
                                    .onSuccess { moved.add(it) }
                                    .onFailure { failed.add("${item.name}: ${it.message ?: "échec"}") }
                            }
                            moved to failed
                        }
                        busy = false
                        val newRecords = added.map { it.record }
                        if (newRecords.isNotEmpty()) {
                            trashRecords = newRecords + trashRecords
                            saveSafTrashRecords(prefs, trashPrefKey, trashRecords)
                        }
                        selectedKeys.clear()
                        val copiedOnly = added.count { !it.sourceRemoved }
                        message = when {
                            errors.isEmpty() && copiedOnly == 0 -> "${newRecords.size} élément(s) déplacé(s) dans la corbeille"
                            errors.isEmpty() -> "Copie de sécurité conservée pour ${copiedOnly} élément(s) : le fournisseur n'a pas supprimé la source"
                            added.isEmpty() -> "Aucun élément déplacé : ${errors.take(2).joinToString(" ; ")}"
                            else -> "${newRecords.size} placé(s), ${errors.size} échec(s) ; ${copiedOnly} source(s) non supprimée(s)"
                        }
                        refreshNow()
                    }
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Mettre à la corbeille") }
            },
            dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Annuler") } }
        )
    }
    if (dialog == "extract" && activeEntry != null) {
        val archive = activeEntry!!
        AlertDialog(
            onDismissRequest = { dialog = ""; activeEntry = null },
            title = { Text("Extraire l’archive ?") },
            text = { Text("Le contenu de ${archive.name} sera extrait dans le dossier courant. Les chemins dangereux, les doublons et les archives trop volumineuses seront refusés.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = ""
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { extractSafZip(context, archive.document, currentDirectory ?: error("Dossier indisponible")) } }
                        busy = false
                        message = result.fold({ "Archive extraite (${it} élément(s))" }, { "Extraction impossible : ${it.message ?: "archive invalide"}" })
                        activeEntry = null
                        refreshNow()
                    }
                }) { Text("Extraire ici") }
            },
            dismissButton = { TextButton(onClick = { dialog = ""; activeEntry = null }) { Text("Annuler") } }
        )
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SafFileRow(
    entry: SafEntry,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val accent = safAccent(entry)
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 1.5.dp else 0.5.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            SafFileIcon(entry, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    if (entry.isDirectory) "Dossier" else "${formatSafSize(entry.size)} • ${entry.mime ?: "Fichier"}",
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) Icon(Icons.Default.CheckCircle, "Sélectionné", tint = MaterialTheme.colorScheme.primary)
            else Icon(Icons.Default.ChevronRight, null, tint = accent.copy(alpha = 0.8f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SafFileCard(
    entry: SafEntry,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 1.5.dp else 0.5.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SafFileIcon(entry, 54.dp)
                if (selected) Icon(Icons.Default.CheckCircle, "Sélectionné", tint = MaterialTheme.colorScheme.primary)
            }
            Text(entry.name, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (entry.isDirectory) "Dossier" else formatSafSize(entry.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SafFileIcon(entry: SafEntry, size: androidx.compose.ui.unit.Dp) {
    val accent = safAccent(entry)
    val icon = when {
        entry.isDirectory -> Icons.Default.Folder
        entry.name.endsWith(".pdf", true) -> Icons.Default.PictureAsPdf
        entry.name.endsWith(".apk", true) -> Icons.Default.Android
        entry.name.endsWith(".zip", true) || entry.name.endsWith(".7z", true) || entry.name.endsWith(".rar", true) -> Icons.Default.FolderZip
        entry.name.endsWith(".jpg", true) || entry.name.endsWith(".jpeg", true) || entry.name.endsWith(".png", true) || entry.name.endsWith(".webp", true) -> Icons.Default.Image
        entry.name.endsWith(".mp4", true) || entry.name.endsWith(".mkv", true) || entry.name.endsWith(".webm", true) -> Icons.Default.Movie
        entry.name.endsWith(".mp3", true) || entry.name.endsWith(".wav", true) || entry.name.endsWith(".m4a", true) -> Icons.Default.MusicNote
        entry.name.endsWith(".txt", true) || entry.name.endsWith(".json", true) || entry.name.endsWith(".xml", true) || entry.name.endsWith(".kt", true) -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                1.dp,
                if (entry.isDirectory) MaterialTheme.colorScheme.outlineVariant else accent.copy(alpha = 0.42f),
                RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = if (entry.isDirectory) "Dossier" else "Fichier",
            tint = if (entry.isDirectory) MaterialTheme.colorScheme.onSurface else accent,
            modifier = Modifier.size(size * 0.62f)
        )
        if (!entry.isDirectory) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(5.dp)
                    .size(6.dp).background(accent, RoundedCornerShape(50))
            )
        }
    }
}


private fun findSafChildByName(parent: DocumentFile, name: String): DocumentFile? =
    runCatching { parent.listFiles().firstOrNull { it.name?.equals(name, ignoreCase = true) == true } }.getOrNull()

private fun isValidSafLeafName(name: String): Boolean {
    val clean = name.trim()
    return clean.isNotEmpty() && clean !in setOf(".", "..") && !clean.contains('/') &&
        !clean.contains('\\') && !clean.contains('\u0000') && clean.toByteArray(Charsets.UTF_8).size <= 255
}

private fun renamedSafName(entry: SafEntry, prefix: String, suffix: String): String {
    val extension = if (!entry.isDirectory && entry.name.lastIndexOf('.') > 0) ".${entry.name.substringAfterLast('.')}" else ""
    val stem = if (extension.isNotEmpty()) entry.name.removeSuffix(extension) else entry.name
    return prefix.trim() + stem + suffix.trim() + extension
}

private fun renameSafSelection(entries: List<SafEntry>, root: DocumentFile?, prefix: String, suffix: String): String {
    if (prefix.isBlank() && suffix.isBlank()) return "Ajoute un début ou une fin au nom."
    val selected = collapseSafSelection(entries)
    if (selected.isEmpty()) return "Aucun élément valide à renommer."
    data class Plan(val entry: SafEntry, val parent: DocumentFile, val newName: String)
    val plans = mutableListOf<Plan>()
    val targetKeys = mutableSetOf<String>()
    for (entry in selected) {
        val parentPath = entry.relativePath.substringBeforeLast('/', "")
        val parent = findSafDirectory(root ?: return "Racine SAF indisponible", parentPath)
            ?: return "Dossier parent indisponible pour ${entry.name}. Aucun changement effectué."
        if (!entry.document.canWrite() || !parent.canWrite()) return "Écriture refusée pour ${entry.name}. Aucun changement effectué."
        val newName = renamedSafName(entry, prefix, suffix)
        if (!isValidSafLeafName(newName)) return "Nom invalide pour ${entry.name} (255 octets maximum). Aucun changement effectué."
        if (newName == entry.name) continue
        val targetKey = parent.uri.toString() + "/" + newName.lowercase(Locale.ROOT)
        if (!targetKeys.add(targetKey)) return "Plusieurs éléments produiraient le nom $newName. Aucun changement effectué."
        val existing = findSafChildByName(parent, newName)
        if (existing != null && existing.uri != entry.document.uri) return "Le nom $newName existe déjà (y compris avec une différence de majuscules). Aucun changement effectué."
        plans.add(Plan(entry, parent, newName))
    }
    if (plans.isEmpty()) return "Aucun nom n'a changé."
    var renamed = 0
    val failures = mutableListOf<String>()
    plans.forEach { plan ->
        val ok = runCatching { plan.entry.document.renameTo(plan.newName) }.getOrDefault(false)
        if (ok) renamed++ else failures.add(plan.entry.name)
    }
    return when {
        failures.isEmpty() -> "$renamed élément(s) renommé(s) avec succès."
        renamed == 0 -> "Aucun élément renommé. Échec d'accès ou opération non prise en charge : ${failures.take(3).joinToString()}"
        else -> "$renamed élément(s) renommé(s), ${failures.size} échec(s) : ${failures.take(3).joinToString()}. Vérifie les éléments restants."
    }
}

private fun collapseSafSelection(entries: List<SafEntry>): List<SafEntry> {
    val selectedDirectories = entries.filter { it.isDirectory }
    return entries.filter { candidate ->
        selectedDirectories.none { parent ->
            parent.key != candidate.key && candidate.relativePath.startsWith(parent.relativePath.trimEnd('/') + "/")
        }
    }
}

private fun safCategoryMatches(entry: SafEntry, category: String): Boolean {
    if (category == "Tous" || entry.isDirectory) return true
    val ext = entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when (category) {
        "Images" -> ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg")
        "Vidéos" -> ext in setOf("mp4", "mkv", "mov", "webm", "3gp", "avi")
        "Audio" -> ext in setOf("mp3", "wav", "m4a", "ogg", "flac", "aac")
        "Documents" -> ext in setOf("pdf", "doc", "docx", "txt", "rtf", "odt", "xls", "xlsx", "ppt", "pptx", "json", "xml")
        "Archives" -> ext in setOf("zip", "rar", "7z", "tar", "gz", "xz")
        else -> true
    }
}

private fun formatSafSize(bytes: Long): String {
    if (bytes < 0L) return "Taille inconnue"
    if (bytes < 1024L) return "$bytes octet${if (bytes > 1L) "s" else ""}"
    val units = arrayOf("Ko", "Mo", "Go", "To")
    var value = bytes.toDouble()
    var index = -1
    do { value /= 1024.0; index++ } while (value >= 1024.0 && index < units.lastIndex)
    return String.format(Locale.getDefault(), "%.1f %s", value, units[index])
}

private fun uniqueSafName(parent: DocumentFile, desiredName: String): String {
    if (findSafChildByName(parent, desiredName) == null) return desiredName
    val extension = desiredName.substringAfterLast('.', "").takeIf { it.isNotEmpty() && !desiredName.endsWith('.') }
    val base = if (extension == null) desiredName else desiredName.removeSuffix(".$extension")
    var index = 1
    while (index < 10000) {
        val candidate = if (extension == null) "$base ($index)" else "$base ($index).$extension"
        if (findSafChildByName(parent, candidate) == null) return candidate
        index++
    }
    error("Trop de fichiers portent déjà un nom similaire")
}

private class SafCopyBudget(var visited: Int = 0, var bytes: Long = 0L)

private fun copySafDocument(
    context: Context,
    source: DocumentFile,
    targetParent: DocumentFile,
    budget: SafCopyBudget = SafCopyBudget(),
    depth: Int = 0,
    desiredName: String? = null
): DocumentFile {
    if (depth > 64) error("Arborescence trop profonde pour être copiée en toute sécurité")
    if (!source.exists() || !source.canRead()) error("Lecture refusée : ${source.name ?: "élément"}")
    if (!targetParent.exists() || !targetParent.isDirectory || !targetParent.canWrite()) error("Écriture refusée dans le dossier de destination")
    budget.visited++
    if (budget.visited > 50000) error("Copie arrêtée : limite de 50 000 éléments atteinte")
    val sourceName = source.name ?: "Sans nom"
    val targetName = uniqueSafName(targetParent, desiredName ?: sourceName)
    if (source.isDirectory) {
        val targetDirectory = targetParent.createDirectory(targetName) ?: error("Impossible de créer le dossier $targetName")
        try {
            source.listFiles().forEach { child -> copySafDocument(context, child, targetDirectory, budget, depth + 1) }
            return targetDirectory
        } catch (error: Exception) {
            deleteSafTree(targetDirectory)
            throw error
        }
    }
    val targetFile = targetParent.createFile(source.type ?: "application/octet-stream", targetName)
        ?: error("Impossible de créer le fichier $targetName")
    try {
        val input = context.contentResolver.openInputStream(source.uri) ?: error("Lecture impossible : $sourceName")
        input.use { sourceStream ->
            val output = context.contentResolver.openOutputStream(targetFile.uri, "w")
                ?: error("Écriture impossible : $targetName")
            output.use { targetStream ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = sourceStream.read(buffer)
                    if (count < 0) break
                    budget.bytes += count.toLong()
                    if (budget.bytes > 8L * 1024L * 1024L * 1024L) error("Limite de copie de 8 Gio atteinte")
                    targetStream.write(buffer, 0, count)
                }
                targetStream.flush()
            }
        }
        return targetFile
    } catch (error: Exception) {
        targetFile.delete()
        throw error
    }
}

private data class SafTrashRecord(
    val trashName: String,
    val originalPath: String,
    val originalName: String,
    val isDirectory: Boolean
)

private data class SafTrashOutcome(val record: SafTrashRecord, val sourceRemoved: Boolean)

private fun loadSafTrashRecords(prefs: android.content.SharedPreferences, key: String): List<SafTrashRecord> {
    return runCatching {
        val array = JSONArray(prefs.getString(key, "[]") ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val trashName = item.optString("trashName")
                val originalName = item.optString("originalName")
                if (trashName.isNotBlank() && originalName.isNotBlank()) {
                    add(SafTrashRecord(trashName, item.optString("originalPath"), originalName, item.optBoolean("isDirectory")))
                }
            }
        }
    }.getOrDefault(emptyList())
}

private fun saveSafTrashRecords(prefs: android.content.SharedPreferences, key: String, records: List<SafTrashRecord>) {
    val array = JSONArray()
    records.forEach { record ->
        array.put(JSONObject().put("trashName", record.trashName)
            .put("originalPath", record.originalPath)
            .put("originalName", record.originalName)
            .put("isDirectory", record.isDirectory))
    }
    prefs.edit().putString(key, array.toString()).apply()
}

private fun findSafDirectory(root: DocumentFile, relativePath: String): DocumentFile? {
    var cursor = root
    for (part in relativePath.split('/').filter { it.isNotBlank() }) {
        cursor = cursor.findFile(part)?.takeIf { it.isDirectory } ?: return null
    }
    return cursor
}

private fun deleteSafTree(document: DocumentFile, depth: Int = 0, budget: IntArray = intArrayOf(0)): Boolean {
    if (depth > 64) return false
    budget[0]++
    if (budget[0] > 50000) return false
    if (document.isDirectory) {
        val childrenResult = runCatching { document.listFiles().toList() }
        if (childrenResult.isFailure) return false
        val children = childrenResult.getOrNull() ?: return false
        for (child in children) if (!deleteSafTree(child, depth + 1, budget)) return false
    }
    return runCatching { document.delete() }.getOrDefault(false)
}

private fun trashSafEntry(context: Context, root: DocumentFile?, entry: SafEntry): SafTrashOutcome {
    val rootDocument = root ?: error("Racine SAF indisponible")
    if (entry.relativePath == ".ZERNEX_Corbeille" || entry.relativePath.startsWith(".ZERNEX_Corbeille/")) {
        error("La corbeille ne peut pas être déplacée dans elle-même")
    }
    val trashFolder = rootDocument.findFile(".ZERNEX_Corbeille")?.takeIf { it.isDirectory }
        ?: rootDocument.createDirectory(".ZERNEX_Corbeille")
        ?: error("Impossible de créer la corbeille dans ce dossier")
    val copy = copySafDocument(context, entry.document, trashFolder)
    val removed = deleteSafTree(entry.document)
    val record = SafTrashRecord(
        trashName = copy.name ?: error("Nom de l'élément dans la corbeille indisponible"),
        originalPath = entry.relativePath.substringBeforeLast('/', ""),
        originalName = entry.name,
        isDirectory = entry.isDirectory
    )
    return SafTrashOutcome(record, removed)
}

private fun restoreSafTrash(context: Context, root: DocumentFile?, record: SafTrashRecord): String {
    val rootDocument = root ?: error("Racine SAF indisponible")
    val trashFolder = rootDocument.findFile(".ZERNEX_Corbeille")?.takeIf { it.isDirectory }
        ?: error("Dossier de corbeille introuvable")
    val source = trashFolder.findFile(record.trashName) ?: error("Élément introuvable dans la corbeille")
    val destination = findSafDirectory(rootDocument, record.originalPath)
        ?: error("Le dossier d'origine n'existe plus : ${record.originalPath}")
    val restored = copySafDocument(context, source, destination, desiredName = record.originalName)
    if (!deleteSafTree(source)) {
        deleteSafTree(restored)
        error("Le fichier restauré existe, mais sa copie dans la corbeille n'a pas pu être retirée")
    }
    return restored.name ?: record.originalName
}

private fun deleteSafTrash(root: DocumentFile?, record: SafTrashRecord): Boolean {
    val trashFolder = root?.findFile(".ZERNEX_Corbeille")?.takeIf { it.isDirectory } ?: return false
    val document = trashFolder.findFile(record.trashName) ?: return false
    return deleteSafTree(document)
}

private fun isSafImageFile(name: String): Boolean = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in
    setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "avif")

private fun isSafZipFile(name: String): Boolean = name.endsWith(".zip", ignoreCase = true)

private fun isSafTextFile(name: String): Boolean = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in
    setOf("txt", "md", "csv", "json", "xml", "html", "htm", "log", "ini", "conf", "properties", "kt", "java", "js", "css", "yaml", "yml", "sh", "py")

private fun readSafText(context: Context, document: DocumentFile): String {
    val maxBytes = 1024 * 1024
    val input = context.contentResolver.openInputStream(document.uri) ?: error("Ouverture impossible")
    val output = ByteArrayOutputStream()
    input.use { stream ->
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (output.size() + count > maxBytes) error("Le fichier dépasse la limite d'aperçu de 1 Mio")
            output.write(buffer, 0, count)
        }
    }
    return output.toByteArray().toString(Charsets.UTF_8)
}

private fun writeSafText(context: Context, document: DocumentFile, text: String) {
    val output = context.contentResolver.openOutputStream(document.uri, "wt")
        ?: context.contentResolver.openOutputStream(document.uri, "w")
        ?: error("Ouverture en écriture impossible")
    output.use { stream -> stream.write(text.toByteArray(Charsets.UTF_8)); stream.flush() }
}

private fun openSafExternally(context: Context, entry: SafEntry) {
    runCatching {
        val mime = entry.mime ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(entry.name.substringAfterLast('.', "").lowercase()) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(entry.document.uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Ouvrir avec"))
    }.onFailure {
        android.widget.Toast.makeText(context, "Aucune application ne peut ouvrir ce fichier : ${it.message ?: "format non pris en charge"}", android.widget.Toast.LENGTH_LONG).show()
    }
}

private data class SafDetails(val size: Long, val items: Int, val truncated: Boolean, val md5: String?)

private fun calculateSafDetails(context: Context, document: DocumentFile): SafDetails {
    if (!document.isDirectory) {
        val hash = runCatching {
            val digest = MessageDigest.getInstance("MD5")
            val input = context.contentResolver.openInputStream(document.uri) ?: error("Lecture impossible")
            input.use { stream ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
        return SafDetails(document.length().coerceAtLeast(0L), 1, false, hash)
    }
    val stack = ArrayDeque<DocumentFile>()
    stack.add(document)
    var total = 0L
    var count = 0
    var truncated = false
    while (stack.isNotEmpty()) {
        val current = stack.removeLast()
        val children = runCatching { current.listFiles().toList() }.getOrElse { emptyList() }
        for (child in children) {
            count++
            if (count > 50000) { truncated = true; break }
            if (child.isDirectory) stack.add(child) else total += child.length().coerceAtLeast(0L)
        }
        if (truncated) break
    }
    return SafDetails(total, count.coerceAtMost(50000), truncated, null)
}

private data class SafZipBudget(var entries: Int = 0, var bytes: Long = 0L)

private fun createSafZip(context: Context, sources: List<DocumentFile>, targetParent: DocumentFile): DocumentFile {
    if (sources.isEmpty()) error("Aucun élément sélectionné")
    val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
    val archiveName = uniqueSafName(targetParent, "ZERNEX_$stamp.zip")
    val archive = targetParent.createFile("application/zip", archiveName) ?: error("Création de l'archive impossible")
    val budget = SafZipBudget()
    try {
        val output = context.contentResolver.openOutputStream(archive.uri, "w") ?: error("Écriture de l'archive impossible")
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            fun add(document: DocumentFile, archivePath: String, depth: Int) {
                if (depth > 64) error("Arborescence trop profonde")
                budget.entries++
                if (budget.entries > 10000) error("Archive trop volumineuse : plus de 10 000 éléments")
                if (document.isDirectory) {
                    val normalized = archivePath.trimEnd('/') + "/"
                    zip.putNextEntry(ZipEntry(normalized))
                    zip.closeEntry()
                    val children = document.listFiles().sortedBy { it.name?.lowercase(Locale.ROOT) ?: "" }
                    children.forEach { child -> add(child, "$normalized${child.name ?: "Sans nom"}", depth + 1) }
                } else {
                    zip.putNextEntry(ZipEntry(archivePath))
                    val input = context.contentResolver.openInputStream(document.uri) ?: error("Lecture impossible : ${document.name}")
                    input.use { stream ->
                        val buffer = ByteArray(32 * 1024)
                        var fileBytes = 0L
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            fileBytes += count
                            budget.bytes += count
                            if (fileBytes > 256L * 1024 * 1024 || budget.bytes > 512L * 1024 * 1024) {
                                error("Limite de taille de l'archive dépassée")
                            }
                            zip.write(buffer, 0, count)
                        }
                    }
                    zip.closeEntry()
                }
            }
            sources.forEach { source -> add(source, source.name ?: "Sans nom", 0) }
        }
        return archive
    } catch (error: Exception) {
        deleteSafTree(archive)
        throw error
    }
}

private fun extractSafZip(context: Context, archive: DocumentFile, targetParent: DocumentFile): Int {
    val input = context.contentResolver.openInputStream(archive.uri) ?: error("Lecture de l'archive impossible")
    val created = mutableListOf<DocumentFile>()
    val budget = SafZipBudget()
    try {
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                budget.entries++
                if (budget.entries > 10000) error("Archive refusée : plus de 10 000 éléments")
                val rawName = entry.name
                if (rawName.isBlank() || rawName.startsWith('/') || rawName.startsWith('\\') || rawName.contains('\\') || rawName.contains('\u0000') || rawName.contains(':')) {
                    error("Chemin interdit dans l'archive")
                }
                val parts = rawName.split('/').filter { it.isNotEmpty() }
                if (parts.isEmpty() || parts.any { it == "." || it == ".." }) error("Chemin dangereux dans l'archive")
                var parent = targetParent
                val directoryParts = if (entry.isDirectory) parts else parts.dropLast(1)
                for (part in directoryParts) {
                    val existing = parent.findFile(part)
                    parent = when {
                        existing == null -> (parent.createDirectory(part) ?: error("Création du dossier $part impossible")).also { created.add(it) }
                        existing.isDirectory -> existing
                        else -> error("Conflit de type pour le dossier $part")
                    }
                }
                if (!entry.isDirectory) {
                    val requestedName = parts.last()
                    val safeName = uniqueSafName(parent, requestedName)
                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(requestedName.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
                    val target = parent.createFile(mime, safeName) ?: error("Création du fichier $safeName impossible")
                    created.add(target)
                    try {
                        val output = context.contentResolver.openOutputStream(target.uri, "w") ?: error("Écriture de $safeName impossible")
                        output.use { stream ->
                            val buffer = ByteArray(32 * 1024)
                            var fileBytes = 0L
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                fileBytes += count
                                budget.bytes += count
                                if (fileBytes > 256L * 1024 * 1024 || budget.bytes > 512L * 1024 * 1024) {
                                    error("Limite d'extraction dépassée")
                                }
                                stream.write(buffer, 0, count)
                            }
                        }
                    } catch (error: Exception) {
                        deleteSafTree(target)
                        throw error
                    }
                }
                zip.closeEntry()
            }
        }
        return budget.entries
    } catch (error: Exception) {
        created.asReversed().forEach { runCatching { deleteSafTree(it) } }
        throw error
    }
}

private fun shareSafDocuments(context: Context, entries: List<SafEntry>) {
    if (entries.isEmpty()) return
    val uris = ArrayList(entries.map { it.document.uri })
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "*/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, "Fichiers", uris.first()).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Partager les fichiers")) }
        .onFailure { android.widget.Toast.makeText(context, "Partage impossible : ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SafTextEditorPage(
    entry: SafEntry,
    text: String,
    loading: Boolean,
    readOnly: Boolean,
    saving: Boolean,
    onTextChange: (String) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(when { loading -> "Lecture…"; saving -> "Enregistrement…"; readOnly -> "Lecture seule"; else -> "Éditeur de texte" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour") } },
                actions = {
                    TextButton(enabled = !loading && !readOnly && !saving, onClick = onSave) {
                        Icon(Icons.Default.Save, null); Spacer(Modifier.width(5.dp)); Text("Enregistrer")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(entry.relativePath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Surface(Modifier.weight(1f).fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    readOnly = loading || readOnly,
                    enabled = !loading && !saving,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    minLines = 12,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    placeholder = { Text(if (readOnly) "Aperçu en lecture seule" else "Commencer à écrire…") }
                )
            }
            if (readOnly) Text("Ce fichier ou ce fournisseur autorise uniquement la lecture.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SafPreviewPage(entry: SafEntry, onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(formatSafSize(entry.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour") } },
                actions = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, "Fermer") } }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).navigationBarsPadding().padding(12.dp), contentAlignment = Alignment.Center) {
            AsyncImage(model = entry.document.uri, contentDescription = entry.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun SafPropertiesPage(entry: SafEntry, context: Context, onBack: () -> Unit) {
    var details by remember(entry.key) { mutableStateOf<SafDetails?>(null) }
    var failed by remember(entry.key) { mutableStateOf(false) }
    LaunchedEffect(entry.key) {
        val result = withContext(Dispatchers.IO) { runCatching { calculateSafDetails(context, entry.document) } }
        details = result.getOrNull()
        failed = result.isFailure
    }
    ZernexPageScaffold(title = "Informations", subtitle = entry.name, onBack = onBack) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, null, tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, modifier = Modifier.size(44.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(if (entry.isDirectory) "Dossier" else entry.mime ?: "Type inconnu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DetailLine("Taille", if (entry.isDirectory && details != null) formatSafSize(details!!.size) else formatSafSize(entry.size))
                if (entry.isDirectory) DetailLine("Éléments analysés", details?.let { it.items.toString() + if (it.truncated) " (analyse partielle)" else "" } ?: if (failed) "Indisponible" else "Calcul en cours…")
                if (!entry.isDirectory) DetailLine("MD5", details?.md5 ?: if (failed) "Indisponible" else "Calcul en cours…")
                DetailLine("Modifié", if (entry.modified > 0) SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(entry.modified)) else "Inconnue")
                DetailLine("Accès", "${if (entry.document.canRead()) "Lecture" else "Pas de lecture"} • ${if (entry.document.canWrite()) "Écriture" else "Pas d'écriture"}")
                DetailLine("Emplacement", entry.relativePath)
                DetailLine("URI", entry.document.uri.toString())
            }
        }
    }
}

@Composable
private fun SafFavoritesPage(paths: List<String>, onOpen: (String) -> Unit, onRemove: (String) -> Unit, onBack: () -> Unit) {
    ZernexPageScaffold(title = "Dossiers favoris", subtitle = "${paths.size} emplacements", onBack = onBack) {
        if (paths.isEmpty()) ZernexEmptyState("Aucun favori", "Ajoute le dossier courant depuis le menu.") { Icon(Icons.Default.Bookmarks, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(42.dp)) }
        else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(paths, key = { it }) { path ->
                ListItem(
                    headlineContent = { Text(path.substringAfterLast('/').ifBlank { "Racine sélectionnée" }, maxLines = 1) },
                    supportingContent = { Text(if (path.isBlank()) "/" else path, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingContent = { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { IconButton(onClick = { onRemove(path) }) { Icon(Icons.Default.BookmarkRemove, "Retirer des favoris") } },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(path) }
                )
            }
        }
    }
}

@Composable
private fun SafTrashPage(records: List<SafTrashRecord>, busy: Boolean, onRestore: (SafTrashRecord) -> Unit, onDelete: (SafTrashRecord) -> Unit, onBack: () -> Unit) {
    ZernexPageScaffold(title = "Corbeille ZERNEX", subtitle = "${records.size} éléments", onBack = onBack) {
        if (records.isEmpty()) ZernexEmptyState("Corbeille vide", "Les éléments déplacés ici apparaîtront dans cette liste.") { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(42.dp)) }
        else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(records, key = { it.trashName }) { record ->
                ListItem(
                    headlineContent = { Text(record.originalName, maxLines = 1) },
                    supportingContent = { Text(record.originalPath.ifBlank { "/" }, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingContent = { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.secondary) },
                    trailingContent = {
                        Row {
                            IconButton(enabled = !busy, onClick = { onRestore(record) }) { Icon(Icons.Default.Restore, "Restaurer", tint = MaterialTheme.colorScheme.primary) }
                            IconButton(enabled = !busy, onClick = { onDelete(record) }) { Icon(Icons.Default.DeleteForever, "Supprimer définitivement", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SafPurgeConfirmation(record: SafTrashRecord, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supprimer définitivement ?") },
        text = { Text("${record.originalName} ne pourra plus être restauré.") },
        confirmButton = { TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Supprimer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun SafSettingsPage(
    showHidden: Boolean,
    layout: SafLayout,
    sort: SafSort,
    ascending: Boolean,
    onShowHidden: (Boolean) -> Unit,
    onLayout: (SafLayout) -> Unit,
    onSort: (SafSort) -> Unit,
    onAscending: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    ZernexPageScaffold(title = "Paramètres", subtitle = "Affichage et navigation", onBack = onBack) {
        SettingsGroup {
            SettingsSwitchRow("Fichiers cachés", "Afficher les éléments dont le nom commence par un point", showHidden, onShowHidden)
            SettingsSwitchRow("Ordre croissant", "Désactive pour inverser le tri", ascending, onAscending)
        }
        SettingsGroup {
            Text("Présentation", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = layout == SafLayout.LIST, onClick = { onLayout(SafLayout.LIST) }, label = { Text("Liste") }, leadingIcon = { Icon(Icons.Default.ViewList, null) })
                FilterChip(selected = layout == SafLayout.GRID, onClick = { onLayout(SafLayout.GRID) }, label = { Text("Grille") }, leadingIcon = { Icon(Icons.Default.GridView, null) })
            }
        }
        SettingsGroup {
            Text("Trier par", fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SafSort.entries.forEach { option ->
                    FilterChip(selected = sort == option, onClick = { onSort(option) }, label = { Text(when (option) { SafSort.NAME -> "Nom"; SafSort.SIZE -> "Taille"; SafSort.DATE -> "Date"; SafSort.TYPE -> "Type" }) })
                }
            }
        }
    }
}
