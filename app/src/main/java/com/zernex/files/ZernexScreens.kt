package com.zernex.files

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Standard full-page surface for first-class sections (not a floating dialog). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ZernexPageScaffold(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        if (!subtitle.isNullOrBlank()) {
                            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
internal fun ZernexEmptyState(title: String, description: String, icon: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 260.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            icon()
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
