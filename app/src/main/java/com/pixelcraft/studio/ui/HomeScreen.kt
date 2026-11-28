package com.pixelcraft.studio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pixelcraft.studio.R
import com.pixelcraft.studio.core.ads.AdManager
import com.pixelcraft.studio.core.ui.BottomBannerAd
import com.pixelcraft.studio.data.LocalStorageManager
import com.pixelcraft.studio.model.PixelCanvasDocument
import com.pixelcraft.studio.model.PixelTemplate
import com.pixelcraft.studio.model.PixelTemplates
import com.pixelcraft.studio.model.SavedProjectSummary
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    storageManager: LocalStorageManager,
    adManager: AdManager,
    isRtl: Boolean,
    onOpenDocument: (PixelCanvasDocument) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPhotoEditor: () -> Unit = {},
    onOpenDesignStudio: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var savedProjects by remember { mutableStateOf<List<SavedProjectSummary>>(emptyList()) }
    var showNewCanvasDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("Pixel Art") }
    var selectedSize by remember { mutableIntStateOf(16) }

    fun refreshProjects() {
        scope.launch {
            savedProjects = storageManager.listSavedProjects()
        }
    }

    LaunchedEffect(Unit) {
        refreshProjects()
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                storageManager.importImageAsPixelGrid(
                    uri = uri,
                    targetWidth = selectedSize,
                    targetHeight = selectedSize
                ).onSuccess { doc ->
                    onOpenDocument(doc)
                }.onFailure { err ->
                    snackbarHostState.showSnackbar(
                        err.localizedMessage ?: context.getString(R.string.error_generic)
                    )
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.settings)
                            )
                        }
                    }
                )
            },
            bottomBar = {
                BottomBannerAd(adManager = adManager)
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Photo Editor entry
                item {
                    Button(
                        onClick = onOpenPhotoEditor,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.photo_editor_entry),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Design Studio entry
                item {
                    OutlinedButton(
                        onClick = onOpenDesignStudio,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Brush,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.design_studio_entry),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Primary Actions: New Blank Canvas & Import Image
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                newTitle = context.getString(R.string.default_project_title)
                                showNewCanvasDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Brush,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.new_canvas),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.import_image),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Built-in Templates Section
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.templates_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(PixelTemplates.builtInTemplates, key = { it.id }) { tpl ->
                                TemplateCard(
                                    template = tpl,
                                    onClick = {
                                        val doc = PixelCanvasDocument(
                                            id = UUID.randomUUID().toString(),
                                            title = templateDisplayName(context, tpl.nameKey),
                                            width = tpl.width,
                                            height = tpl.height,
                                            pixelsArgb = tpl.buildPixels()
                                        )
                                        onOpenDocument(doc)
                                    }
                                )
                            }
                        }
                    }
                }

                // Saved Local Projects Section
                item {
                    Text(
                        text = stringResource(R.string.saved_projects),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                if (savedProjects.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = 1.dp
                        ) {
                            Text(
                                text = stringResource(R.string.no_saved_projects),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(savedProjects, key = { it.id }) { item ->
                        SavedProjectCard(
                            project = item,
                            onOpen = {
                                scope.launch {
                                    storageManager.loadProject(item.id).onSuccess { loadedDoc ->
                                        onOpenDocument(loadedDoc)
                                    }.onFailure { err ->
                                        snackbarHostState.showSnackbar(
                                            err.localizedMessage ?: context.getString(R.string.error_generic)
                                        )
                                    }
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    if (storageManager.deleteProject(item.id)) {
                                        refreshProjects()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showNewCanvasDialog) {
            AlertDialog(
                onDismissRequest = { showNewCanvasDialog = false },
                title = { Text(text = stringResource(R.string.new_canvas)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it.take(32) },
                            label = { Text(text = stringResource(R.string.project_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = stringResource(R.string.canvas_size),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(16, 24, 32).forEach { sizeOption ->
                                FilterChip(
                                    selected = selectedSize == sizeOption,
                                    onClick = { selectedSize = sizeOption },
                                    label = { Text("${sizeOption}×${sizeOption}") }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showNewCanvasDialog = false
                            val safeTitle = newTitle.trim().ifEmpty {
                                context.getString(R.string.default_project_title)
                            }
                            val doc = PixelTemplates.createBlankDocument(
                                width = selectedSize,
                                height = selectedSize,
                                title = safeTitle
                            )
                            onOpenDocument(doc)
                        }
                    ) {
                        Text(text = stringResource(R.string.create))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewCanvasDialog = false }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun TemplateCard(
    template: PixelTemplate,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .width(128.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(template.accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Brush,
                    contentDescription = null,
                    tint = template.accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = templateDisplayName(context, template.nameKey),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${template.width}×${template.height}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SavedProjectCard(
    project: SavedProjectSummary,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val formattedDate = remember(project.updatedAtEpochMs) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(project.updatedAtEpochMs))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = project.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${project.width}×${project.height} • $formattedDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun templateDisplayName(context: android.content.Context, nameKey: String): String {
    return when (nameKey) {
        "heart" -> context.getString(R.string.template_heart)
        "sun" -> context.getString(R.string.template_sun)
        "gem" -> context.getString(R.string.template_gem)
        else -> nameKey
    }
}
