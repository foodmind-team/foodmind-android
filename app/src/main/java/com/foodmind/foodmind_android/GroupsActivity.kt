package com.foodmind.foodmind_android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.GroupResponse
import kotlinx.coroutines.launch

class GroupsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val client = foodMindApiClient()
        setContent {
            FoodMindTheme {
                GroupsScreen(
                    client, ::openFoodMindRoot,
                    onOpen = { startActivity(GroupWorkspaceActivity.intent(this, it)) },
                    onRecord = { startActivity(RecordEditorActivity.intent(this, "FOOD", null)) },
                )
            }
        }
    }
}

@Composable
private fun GroupsScreen(client: FoodMindApiClient, onNavigate: (FoodMindRoot) -> Unit, onOpen: (String) -> Unit, onRecord: () -> Unit) {
    var groups by remember { mutableStateOf<List<GroupResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(refresh) { loading = true; runCatching { client.groups() }.onSuccess { groups = it; error = null }.onFailure { error = "群组加载失败。" }; loading = false }
    FoodMindRootScaffold(
        FoodMindRoot.GROUPS, "群组", onNavigate,
        topActions = {
            IconButton(onClick = { showJoin = !showJoin }) { Icon(Icons.Outlined.GroupAdd, "加入群组") }
            IconButton(onClick = { showCreate = !showCreate }) { Icon(Icons.Outlined.Add, "创建群组") }
        },
        onRecord = onRecord,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("只在可信群组里共享", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold); Text("口味、记录与推荐会遵守后端群组权限。", color = FoodMindMuted, modifier = Modifier.padding(top = 6.dp)) }
            if (showCreate) item { FoodMindSurfaceCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("创建群组", fontWeight = FontWeight.Bold); OutlinedTextField(name, { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(description, { description = it }, label = { Text("描述") }, modifier = Modifier.fillMaxWidth()); Button(onClick = { scope.launch { runCatching { client.createGroup(name.trim(), description.trim().ifBlank { null }) }.onSuccess { showCreate = false; name = ""; description = ""; refresh++ }.onFailure { error = "创建失败。" } } }, enabled = name.isNotBlank()) { Text("创建") }
            } } }
            if (showJoin) item { FoodMindSurfaceCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("加入可信群组", fontWeight = FontWeight.Bold); OutlinedTextField(token, { token = it }, label = { Text("邀请令牌") }, modifier = Modifier.fillMaxWidth()); Button(onClick = { scope.launch { runCatching { client.joinGroup(token.trim()) }.onSuccess { showJoin = false; token = ""; refresh++ }.onFailure { error = "邀请无效或已过期。" } } }, enabled = token.isNotBlank()) { Text("加入") }
            } } }
            error?.let { item { Text(it, color = FoodMindCoral); TextButton(onClick = { refresh++ }) { Text("重试") } } }
            if (loading) item { CircularProgressIndicator() }
            if (!loading && groups.isEmpty() && error == null) item { FoodMindSurfaceCard { Text("还没有群组。创建一个，或使用邀请令牌加入。") } }
            items(groups, key = { it.id.orEmpty() }) { group ->
                Card(onClick = { group.id?.let(onOpen) }, colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, FoodMindLine)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        FoodMindAvatar(group.name ?: "群")
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(group.name ?: "未命名群组", fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(group.description ?: "共享饮食决定", color = FoodMindMuted, maxLines = 2); Text(group.status ?: "ACTIVE", color = FoodMindGreen, fontSize = 11.sp) }
                        Icon(Icons.Outlined.ChevronRight, null)
                    }
                }
            }
        }
    }
}
