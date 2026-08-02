package com.foodmind.foodmind_android

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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import com.foodmind.foodmind_android.core.network.ChatSessionResponse
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import kotlinx.coroutines.launch

class ChatListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val client = foodMindApiClient()
        setContent { FoodMindTheme { ChatListScreen(client, ::finish) { startActivity(ChatActivity.intent(this, it)) } } }
    }
}

@Composable
private fun ChatListScreen(client: FoodMindApiClient, onBack: () -> Unit, onOpen: (String?) -> Unit) {
    var sessions by remember { mutableStateOf<List<ChatSessionResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { client.chatSessions().items }.onSuccess { sessions = it }.onFailure { error = "Could not load conversations." }; loading = false }
    FoodMindDetailScaffold("Messages", onBack, actions = { IconButton(onClick = { onOpen(null) }) { Icon(Icons.Outlined.Add, "New conversation") } }) { padding ->
        when {
            loading -> CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("FoodMind conversations", fontWeight = FontWeight.ExtraBold); error?.let { Text(it, color = FoodMindCoral) } }
                if (sessions.isEmpty() && error == null) item { FoodMindSurfaceCard { Text("No conversations yet. Use the button above to start one.") } }
                items(sessions, key = { it.id.orEmpty() }) { session ->
                    Card(onClick = { onOpen(session.id) }, colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, FoodMindLine)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            FoodMindAvatar("F")
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(session.title ?: "FoodMind Assistant", fontWeight = FontWeight.Bold); Text(formatFoodMindTimestamp(session.updatedAt), color = FoodMindMuted) }
                            IconButton(onClick = { session.id?.let { id -> scope.launch { runCatching { client.deleteChatSession(id) }.onSuccess { sessions = sessions.filterNot { it.id == id } } } } }) { Icon(Icons.Outlined.DeleteOutline, "Archive") }
                        }
                    }
                }
            }
        }
    }
}
