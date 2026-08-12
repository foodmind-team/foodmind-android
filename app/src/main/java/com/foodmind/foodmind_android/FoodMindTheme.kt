package com.foodmind.foodmind_android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Mirrors the active Web design tokens in foodmind-web/src/App.css.
val FoodMindPaper = Color(0xFF0D100D)
val FoodMindInk = Color(0xFFF1F4F0)
val FoodMindMuted = Color(0xFF98A29A)
val FoodMindFaint = Color(0xFF69736B)
val FoodMindGreen = Color(0xFF79B78E)
val FoodMindGreenDark = Color(0xFF17261C)
val FoodMindLime = Color(0xFFD9EF74)
val FoodMindLine = Color(0xFF343B34)
val FoodMindLineSoft = Color(0xFF272D27)
val FoodMindSurface = Color(0xFF151915)
val FoodMindSurfaceRaised = Color(0xFF202620)
val FoodMindCoral = Color(0xFFE38A7B)

@Composable
fun FoodMindTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = FoodMindLime,
            onPrimary = Color(0xFF11170F),
            primaryContainer = FoodMindGreenDark,
            onPrimaryContainer = FoodMindInk,
            secondary = FoodMindGreen,
            onSecondary = Color(0xFF11170F),
            surface = FoodMindSurface,
            surfaceVariant = FoodMindSurfaceRaised,
            background = FoodMindPaper,
            onSurface = FoodMindInk,
            onBackground = FoodMindInk,
            outline = FoodMindLine,
            error = FoodMindCoral,
            onError = Color(0xFF28120E),
        ),
        typography = Typography(),
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(18.dp),
        ),
        content = content,
    )
}
