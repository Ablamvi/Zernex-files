package com.zernex.files

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class ZernexFileCategory { FOLDER, MEDIA, DOCUMENT, ARCHIVE, APPLICATION, OTHER }

private val mediaExtensions = setOf(
    "jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "heic", "heif", "avif",
    "mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v",
    "mp3", "wav", "ogg", "oga", "flac", "m4a", "aac", "opus", "wma"
)
private val documentExtensions = setOf(
    "pdf", "doc", "docx", "odt", "rtf", "txt", "md", "csv",
    "xls", "xlsx", "ods", "ppt", "pptx", "odp",
    "kt", "kts", "java", "js", "jsx", "ts", "tsx", "py", "json", "xml",
    "yml", "yaml", "html", "htm", "css", "scss", "sql", "sh", "log"
)
private val archiveExtensions = setOf("zip", "7z", "rar", "tar", "gz", "bz2", "xz", "tgz", "tbz", "tbz2", "txz", "jar")
private val applicationExtensions = setOf("apk", "aab", "apks", "xapk")

internal fun zernexCategoryFor(name: String, isDirectory: Boolean): ZernexFileCategory {
    if (isDirectory) return ZernexFileCategory.FOLDER
    val extension = name.substringAfterLast('.', "").lowercase()
    return when {
        extension in mediaExtensions -> ZernexFileCategory.MEDIA
        extension in documentExtensions -> ZernexFileCategory.DOCUMENT
        extension in archiveExtensions -> ZernexFileCategory.ARCHIVE
        extension in applicationExtensions -> ZernexFileCategory.APPLICATION
        else -> ZernexFileCategory.OTHER
    }
}

internal fun zernexAccentFor(name: String, isDirectory: Boolean): Color =
    when (zernexCategoryFor(name, isDirectory)) {
        ZernexFileCategory.MEDIA -> Color(0xFFE34D4D)
        ZernexFileCategory.DOCUMENT -> Color(0xFF38C978)
        ZernexFileCategory.ARCHIVE, ZernexFileCategory.APPLICATION -> Color(0xFFF4C542)
        ZernexFileCategory.FOLDER, ZernexFileCategory.OTHER -> Color(0xFFB8B8BA)
    }

private data class CategoryShortcut(val filter: String, val label: String, val icon: ImageVector, val accent: Color)

@Composable
internal fun ZernexBrandMark() {
    Box(
        Modifier.size(27.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.FolderOpen, contentDescription = "ZERNEX Files", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(19.dp))
        Row(Modifier.align(Alignment.BottomEnd).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            listOf(Color(0xFFE34D4D), Color(0xFF38C978), Color(0xFFF4C542)).forEach { color ->
                Box(Modifier.size(3.dp).background(color, RoundedCornerShape(50)))
            }
        }
    }
}

@Composable
internal fun ZernexCategoryRail(selected: String, onSelected: (String) -> Unit) {
    val shortcuts = listOf(
        CategoryShortcut("Tous", "Tous", Icons.Default.FolderOpen, MaterialTheme.colorScheme.onSurfaceVariant),
        CategoryShortcut("Images", "Photos", Icons.Default.Image, Color(0xFFE34D4D)),
        CategoryShortcut("Vidéos", "Vidéos", Icons.Default.Movie, Color(0xFFE34D4D)),
        CategoryShortcut("Audio", "Audio", Icons.Default.AudioFile, Color(0xFFE34D4D)),
        CategoryShortcut("Documents", "Docs", Icons.Default.Description, Color(0xFF38C978)),
        CategoryShortcut("Archives", "Archives", Icons.Default.FolderZip, Color(0xFFF4C542))
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        shortcuts.forEach { item ->
            val active = selected == item.filter
            Column(
                Modifier.width(58.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelected(item.filter) }.padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(item.icon, contentDescription = item.label, tint = if (active) MaterialTheme.colorScheme.onPrimary else item.accent, modifier = Modifier.size(20.dp))
                Text(item.label, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun ZernexBottomNavigation(
    modifier: Modifier = Modifier,
    onFiles: () -> Unit,
    onRecent: () -> Unit,
    onFavorites: () -> Unit,
    onTools: () -> Unit
) {
    NavigationBar(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, tonalElevation = 0.dp) {
        NavigationBarItem(selected = true, onClick = onFiles, icon = { Icon(Icons.Default.FolderOpen, contentDescription = "Fichiers") }, label = { Text("Fichiers", fontSize = 10.sp) })
        NavigationBarItem(selected = false, onClick = onRecent, icon = { Icon(Icons.Default.Image, contentDescription = "Récents") }, label = { Text("Récents", fontSize = 10.sp) })
        NavigationBarItem(selected = false, onClick = onFavorites, icon = { Icon(Icons.Default.FolderSpecial, contentDescription = "Favoris") }, label = { Text("Favoris", fontSize = 10.sp) })
        NavigationBarItem(selected = false, onClick = onTools, icon = { Icon(Icons.Default.Settings, contentDescription = "Outils") }, label = { Text("Outils", fontSize = 10.sp) })
    }
}

@Composable
internal fun ZernexToolsPage(
    onBack: () -> Unit, onStorage: () -> Unit, onTrash: () -> Unit,
    onDuplicates: () -> Unit, onPermissions: () -> Unit,
    onSettings: () -> Unit, onChooseSaf: () -> Unit
) {
    ZernexPageScaffold(title = "Outils", subtitle = "Actions et entretien du stockage", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZernexToolCard(Modifier.weight(1f), "Stockage", Icons.Default.Storage, Color(0xFFF4C542), onStorage)
                ZernexToolCard(Modifier.weight(1f), "Corbeille", Icons.Default.DeleteSweep, Color(0xFFE34D4D), onTrash)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZernexToolCard(Modifier.weight(1f), "Doublons", Icons.Default.ContentCopy, Color(0xFF38C978), onDuplicates)
                ZernexToolCard(Modifier.weight(1f), "Accès", Icons.Default.Security, MaterialTheme.colorScheme.onSurfaceVariant, onPermissions)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZernexToolCard(Modifier.weight(1f), "Paramètres", Icons.Default.Settings, MaterialTheme.colorScheme.onSurfaceVariant, onSettings)
                ZernexToolCard(Modifier.weight(1f), "Dossier SAF", Icons.Default.FolderSpecial, MaterialTheme.colorScheme.onSurfaceVariant, onChooseSaf)
            }
        }
    }
}

@Composable
private fun ZernexToolCard(modifier: Modifier, label: String, icon: ImageVector, accent: Color, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable(onClick = onClick), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(23.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
