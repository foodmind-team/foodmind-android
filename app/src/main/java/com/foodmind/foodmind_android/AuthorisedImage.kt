package com.foodmind.foodmind_android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import coil.compose.AsyncImage

internal enum class AuthorisedImageState {
    EMPTY,
    LOADING,
    LOADED,
    FAILED,
}

internal fun initialAuthorisedImageState(model: Any?): AuthorisedImageState =
    if (model == null || model is String && model.isBlank()) AuthorisedImageState.EMPTY
    else AuthorisedImageState.LOADING

@Composable
internal fun AuthorisedImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    emptyLabel: String = "No image",
) {
    var state by remember(model) { mutableStateOf(initialAuthorisedImageState(model)) }
    Box(
        modifier = modifier
            .background(FoodMindSurfaceRaised)
            .semantics { stateDescription = state.name },
        contentAlignment = Alignment.Center,
    ) {
        if (state != AuthorisedImageState.EMPTY) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onLoading = { state = AuthorisedImageState.LOADING },
                onSuccess = { state = AuthorisedImageState.LOADED },
                onError = { state = AuthorisedImageState.FAILED },
            )
        }
        when (state) {
            AuthorisedImageState.LOADING -> CircularProgressIndicator()
            AuthorisedImageState.EMPTY -> ImageStatus(Icons.Outlined.Image, emptyLabel)
            AuthorisedImageState.FAILED -> ImageStatus(Icons.Outlined.BrokenImage, "Image unavailable")
            AuthorisedImageState.LOADED -> Unit
        }
    }
}

@Composable
private fun ImageStatus(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = FoodMindMuted)
        Text(label, color = FoodMindMuted)
    }
}
