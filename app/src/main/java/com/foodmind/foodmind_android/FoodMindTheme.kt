package com.foodmind.foodmind_android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val FoodMindPaper = Color(0xFFFBFCF8)
val FoodMindInk = Color(0xFF17241D)
val FoodMindMuted = Color(0xFF657269)
val FoodMindGreen = Color(0xFF287354)
val FoodMindGreenDark = Color(0xFF143D2D)
val FoodMindLime = Color(0xFFD9EF74)
val FoodMindLine = Color(0xFFE2E9E2)
val FoodMindCoral = Color(0xFFD75E40)

@Composable
fun FoodMindTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = FoodMindGreen,
            onPrimary = Color.White,
            surface = Color.White,
            background = FoodMindPaper,
            onSurface = FoodMindInk,
        ),
        content = content,
    )
}
