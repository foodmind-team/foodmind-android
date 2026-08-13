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
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodmind.foodmind_android.core.network.ChatSessionResponse
import com.foodmind.foodmind_android.feature.chat.CHAT_STARTER_PROMPTS
import com.foodmind.foodmind_android.feature.chat.ChatListUiState
import com.foodmind.foodmind_android.feature.chat.ChatListViewModel
import com.foodmind.foodmind_android.feature.chat.DefaultChatRepository
import com.foodmind.foodmind_android.feature.chat.SharedPreferencesChatDraftStore

class ChatListActivity : ComponentActivity() {
    private lateinit var viewModel: ChatListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(
            this,
            ChatListViewModel.Factory(
                DefaultChatRepository(foodMindApiClient()),
                SharedPreferencesChatDraftStore(applicationContext),
            ),
        )[ChatListViewModel::class.java]
        setContent {
            FoodMindTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                EnhancedChatListScreen(
                    state = state,
                    onBack = ::finish,
                    onOpen = { sessionId, prompt ->
                        startActivity(ChatActivity.intent(this, sessionId, prompt))
                    },
                    onRefresh = viewModel::refresh,
                    onQueryChange = viewModel::updateQuery,
                    onRequestArchive = viewModel::requestArchive,
                    onDismissArchive = viewModel::dismissArchive,
                    onConfirmArchive = viewModel::confirmArchive,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) viewModel.refresh()
    }
}

@Composable
private fun ChatListScreen(
    state: ChatListUiState,
    onBack: () -> Unit,
    onOpen: (String?, String?) -> Unit,
    onRefresh: () -> Unit,
    onRequestArchive: (ChatSessionResponse) -> Unit,
    onDismissArchive: () -> Unit,
    onConfirmArchive: () -> Unit,
) {
    state.archiveCandidate?.let { session ->
        AlertDialog(
            onDismissRequest = onDismissArchive,
            title = { Text("Archive conversation?") },
            text = { Text("“${session.title ?: "FoodMind Chat"}” will be removed from this list.") },
            confirmButton = {
                TextButton(onClick = onConfirmArchive, enabled = !state.isArchiving) {
                    Text(if (state.isArchiving) "Archiving…" else "Archive")
                }
            },
            dismissButton = { TextButton(onClick = onDismissArchive) { Text("Cancel") } },
        )
    }

    FoodMindDetailScaffold(
        "FoodMind Chat",
        onBack,
        actions = {
            IconButton(onClick = { onOpen(null, null) }) {
                Icon(Icons.Outlined.Add, "New conversation")
            }
        },
    ) { padding ->
        when {
            state.isLoading && state.sessions.isEmpty() ->
                CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    FoodMindSurfaceCard {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Read-only FoodMind assistant", fontWeight = FontWeight.Bold)
                            Text(
                                "Ask about authorised FoodMind content or general knowledge. Chat never changes data or executes recommendation and cooking actions.",
                                color = FoodMindMuted,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                item {
                    Text("Start with a prompt", fontWeight = FontWeight.ExtraBold)
                    Column(
                        Modifier.padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CHAT_STARTER_PROMPTS.forEach { prompt ->
                            OutlinedButton(
                                onClick = { onOpen(null, prompt) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(prompt, Modifier.weight(1f)) }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent conversations", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold)
                        if (state.isLoading) CircularProgressIndicator()
                    }
                    state.errorMessage?.let { error ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(error, Modifier.weight(1f), color = FoodMindCoral)
                            TextButton(onClick = onRefresh) { Text("Retry") }
                        }
                    }
                }
                if (state.sessions.isEmpty() && state.errorMessage == null) {
                    item { FoodMindSurfaceCard { Text("No conversations yet. Choose a prompt or use + to start one.") } }
                }
                items(state.sessions, key = { it.id.orEmpty() }) { session ->
                    Card(
                        onClick = { onOpen(session.id, null) },
                        colors = CardDefaults.cardColors(containerColor = FoodMindSurface),
                        border = BorderStroke(1.dp, FoodMindLine),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FoodMindAvatar("F")
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(session.title ?: "FoodMind Chat", fontWeight = FontWeight.Bold)
                                Text(
                                    listOfNotNull(
                                        session.status?.lowercase()?.replaceFirstChar { it.uppercase() },
                                        formatFoodMindTimestamp(session.updatedAt),
                                    ).filter(String::isNotBlank).joinToString(" · "),
                                    color = FoodMindMuted,
                                    fontSize = 13.sp,
                                )
                            }
                            IconButton(onClick = { onRequestArchive(session) }) {
                                Icon(Icons.Outlined.Archive, "Archive conversation")
                            }
                        }
                    }
                }
            }
        }
    }
}
