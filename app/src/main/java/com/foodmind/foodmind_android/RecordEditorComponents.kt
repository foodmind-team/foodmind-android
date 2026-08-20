package com.foodmind.foodmind_android

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.foodmind.foodmind_android.core.network.GroupResponse
import java.io.File

internal data class PendingCameraImage(val uri: Uri, val file: File)

internal fun createPendingCameraImage(context: Context): PendingCameraImage {
    val directory = File(context.cacheDir, "record-images").apply { mkdirs() }
    val file = File.createTempFile("record-", ".jpg", directory)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return PendingCameraImage(uri, file)
}

@Composable
internal fun RecordGroupPicker(
    groups: List<GroupResponse>,
    selectedGroupId: String,
    loading: Boolean,
    error: String?,
    onGroupSelected: (String) -> Unit,
    onRetry: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val choices = remember(groups) { selectableRecordGroups(groups) }
    val selectedGroup = choices.firstOrNull { it.id == selectedGroupId }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { menuOpen = true },
            enabled = !loading && choices.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                selectedGroup?.name ?: when {
                    loading -> "Loading your groups…"
                    choices.isEmpty() -> "No active groups available"
                    else -> "Choose a group"
                },
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Open group choices")
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            choices.forEach { group ->
                DropdownMenuItem(
                    text = { Text(group.name ?: "Untitled group") },
                    onClick = {
                        onGroupSelected(group.id.orEmpty())
                        menuOpen = false
                    },
                )
            }
        }
    }
    error?.let { message ->
        Text(message, color = FoodMindCoral)
        TextButton(onClick = onRetry) { Text("Try again") }
    }
    if (!loading && error == null && choices.isEmpty()) {
        Text("Create or join an active group before sharing this record.", color = FoodMindMuted)
    }
}

@Composable
internal fun RecordImageSourceDialog(
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onChooseFromGallery: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CameraAlt, null)
                    Text("Take a photo", Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onChooseFromGallery, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.PhotoLibrary, null)
                    Text("Choose from gallery", Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
