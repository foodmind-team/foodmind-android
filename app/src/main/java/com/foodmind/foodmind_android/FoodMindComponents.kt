package com.foodmind.foodmind_android

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class FoodMindRoot(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    GROUPS("Groups", Icons.Outlined.Group),
    EXPLORE("Discover", Icons.Outlined.Explore),
    SAVED("Saved", Icons.Outlined.BookmarkBorder),
    ME("Me", Icons.Outlined.Person),
}

fun Context.openFoodMindRoot(destination: FoodMindRoot) {
    val target = when (destination) {
        FoodMindRoot.HOME -> MainActivity::class.java
        FoodMindRoot.GROUPS -> GroupsActivity::class.java
        FoodMindRoot.EXPLORE -> ExploreActivity::class.java
        FoodMindRoot.SAVED -> SavedActivity::class.java
        FoodMindRoot.ME -> ProfileActivity::class.java
    }
    if (this::class.java == target) return
    val intent = Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    if (this is Activity) {
        // Root destinations swap immediately so bottom navigation does not slide the whole window.
        startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    } else {
        startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodMindRootScaffold(
    selected: FoodMindRoot,
    title: String,
    onNavigate: (FoodMindRoot) -> Unit,
    modifier: Modifier = Modifier,
    topActions: @Composable RowScope.() -> Unit = {},
    onRecord: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = FoodMindPaper,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = { Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = FoodMindInk) },
                actions = topActions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        floatingActionButton = {
            onRecord?.let {
                FloatingActionButton(
                    onClick = it,
                    shape = RoundedCornerShape(18.dp),
                    containerColor = FoodMindCoral,
                    contentColor = Color.White,
                ) { Icon(Icons.Outlined.Add, contentDescription = "Add record") }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets.navigationBars,
            ) {
                FoodMindRoot.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == selected,
                        onClick = { onNavigate(item) },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, fontSize = 11.sp, fontWeight = if (item == selected) FontWeight.Bold else FontWeight.Medium) },
                    )
                }
            }
        },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodMindDetailScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = FoodMindPaper,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold, color = FoodMindInk) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        content = content,
    )
}

@Composable
fun FoodMindSurfaceCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, FoodMindLine),
    ) { Box(Modifier.fillMaxWidth().padding(16.dp)) { content() } }
}

@Composable
fun FoodMindAvatar(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(FoodMindGreen, CircleShape).padding(horizontal = 12.dp, vertical = 9.dp),
    ) { Text(label.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold) }
}

fun ComponentActivity.foodMindApiClient(): com.foodmind.foodmind_android.core.network.FoodMindApiClient {
    com.foodmind.foodmind_android.core.network.FoodMindSession.initialize(this)
    return com.foodmind.foodmind_android.core.network.FoodMindApiClient(
        com.foodmind.foodmind_android.core.network.FoodMindNetwork.createApi(
            BuildConfig.FOODMIND_API_BASE_URL,
            com.foodmind.foodmind_android.core.network.FoodMindSession.tokenStore,
        ),
        com.foodmind.foodmind_android.core.network.FoodMindSession.tokenStore,
    )
}
