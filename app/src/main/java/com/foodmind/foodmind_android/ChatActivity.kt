package com.foodmind.foodmind_android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodmind.foodmind_android.core.network.ChatMessageResponse
import com.foodmind.foodmind_android.core.network.ChatMessageSourceResponse
import com.foodmind.foodmind_android.core.network.ExploreItemResponse
import com.foodmind.foodmind_android.feature.chat.CHAT_MESSAGE_LIMIT
import com.foodmind.foodmind_android.feature.chat.ChatDestination
import com.foodmind.foodmind_android.feature.chat.ChatSourceFilter
import com.foodmind.foodmind_android.feature.chat.ChatUiState
import com.foodmind.foodmind_android.feature.chat.ChatViewModel
import com.foodmind.foodmind_android.feature.chat.DefaultChatRepository
import com.foodmind.foodmind_android.feature.chat.PendingChatSource
import com.foodmind.foodmind_android.feature.chat.SharedPreferencesChatDraftStore

class ChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        val initialDraft = intent.getStringExtra(EXTRA_INITIAL_DRAFT).orEmpty()
        val pendingSource = intent.getStringExtra(EXTRA_SOURCE_TYPE)?.let { sourceType ->
            intent.getStringExtra(EXTRA_SOURCE_ID)?.let { sourceId -> PendingChatSource(sourceType, sourceId) }
        }
        val viewModel = ViewModelProvider(
            this,
            ChatViewModel.Factory(
                DefaultChatRepository(foodMindApiClient()),
                SharedPreferencesChatDraftStore(applicationContext),
            ),
        )[ChatViewModel::class.java]
        viewModel.open(sessionId, initialDraft, pendingSource)

        setContent {
            FoodMindTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                EnhancedChatScreen(
                    state = state,
                    onBack = ::finish,
                    onRetryLoad = { viewModel.retry(sessionId, initialDraft, pendingSource) },
                    onDraftChange = viewModel::updateDraft,
                    onSend = viewModel::send,
                    onRetryMessage = viewModel::retryFailedMessage,
                    onEditMessage = viewModel::editFailedMessage,
                    onDismissMessage = viewModel::dismissFailedMessage,
                    onLoadMore = viewModel::loadMore,
                    onSearch = viewModel::updateSearchQuery,
                    onFilter = viewModel::setSourceFilter,
                    onAttach = viewModel::attach,
                    onReattach = viewModel::reattach,
                    onRemoveReference = viewModel::removeReference,
                    onClearReferences = viewModel::clearCurrentReferences,
                    onOpenSource = ::openSource,
                    onCopyAnswer = ::copyAnswer,
                    onShareAnswer = ::shareAnswer,
                    onDestination = ::openDestination,
                    onLogin = { startActivity(Intent(this, LoginActivity::class.java)) },
                )
            }
        }
    }

    private fun openSource(source: ChatMessageSourceResponse) {
        val sourceId = source.sourceId ?: return
        when (source.sourceType) {
            "FOOD_RECORD" -> startActivity(RecordDetailActivity.intent(this, "FOOD", sourceId))
            "FOOD_PRODUCT" -> startActivity(CatalogueDetailActivity.intent(this, "FOOD_PRODUCT", sourceId))
            "PLACE" -> startActivity(CatalogueDetailActivity.intent(this, "PLACE", sourceId))
        }
    }

    private fun copyAnswer(answer: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("FoodMind answer", answer))
        Toast.makeText(this, "Answer copied", Toast.LENGTH_SHORT).show()
    }

    private fun shareAnswer(message: ChatMessageResponse) {
        val sourceTitles = message.sources
            .orEmpty()
            .mapNotNull { it.title?.takeIf(String::isNotBlank) }
            .distinct()
        val shareText = buildString {
            append(message.content.orEmpty())
            if (sourceTitles.isNotEmpty()) {
                append("\n\nSources: ")
                append(sourceTitles.joinToString())
            }
            append("\n\nShared from FoodMind. Verify important dietary or health information.")
        }
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                },
                "Share FoodMind answer",
            ),
        )
    }

    private fun openDestination(destination: ChatDestination) {
        val target = when (destination) {
            ChatDestination.INVENTORY -> InventoryActivity::class.java
            ChatDestination.SHOPPING_LISTS -> ShoppingListsActivity::class.java
            ChatDestination.SAVED_RECIPES -> RecipeLibraryActivity::class.java
            ChatDestination.COOKING_PLANS -> CookingPlansActivity::class.java
            ChatDestination.RECOMMENDATIONS -> MainActivity::class.java
            ChatDestination.EXPLORE -> ExploreActivity::class.java
        }
        startActivity(Intent(this, target))
    }

    companion object {
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_INITIAL_DRAFT = "initial_draft"
        private const val EXTRA_SOURCE_TYPE = "source_type"
        private const val EXTRA_SOURCE_ID = "source_id"

        fun intent(context: Context, sessionId: String?, initialDraft: String? = null): Intent =
            Intent(context, ChatActivity::class.java).apply {
                sessionId?.let { putExtra(EXTRA_SESSION_ID, it) }
                initialDraft?.let { putExtra(EXTRA_INITIAL_DRAFT, it) }
            }

        fun intentWithSource(
            context: Context,
            sourceType: String,
            sourceId: String,
            initialDraft: String? = null,
        ): Intent = intent(context, null, initialDraft).apply {
            putExtra(EXTRA_SOURCE_TYPE, sourceType)
            putExtra(EXTRA_SOURCE_ID, sourceId)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatScreen(
    state: ChatUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onLoadMore: () -> Unit,
    onSearch: (String) -> Unit,
    onFilter: (ChatSourceFilter) -> Unit,
    onAttach: (ExploreItemResponse) -> Unit,
    onRemoveReference: (String?) -> Unit,
    onOpenSource: (ChatMessageSourceResponse) -> Unit,
    onOpenHome: () -> Unit,
    onLogin: () -> Unit,
) {
    var showSources by remember { mutableStateOf(false) }
    FoodMindDetailScaffold(state.title, onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.padding(24.dp))
                state.sessionId == null -> ChatLoadError(state, onRetry, onLogin)
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            FoodMindSurfaceCard {
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("Read-only FoodMind assistant", fontWeight = FontWeight.Bold)
                                    Text(
                                        "It can use authorised FoodMind sources or general knowledge. It never changes data or executes recommendation or cooking actions. Source cards appear only for grounded answers.",
                                        color = FoodMindMuted,
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                        if (state.messages.isEmpty()) {
                            item {
                                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Ask a read-only question or attach a FoodMind source.", color = FoodMindMuted)
                                    listOf(
                                        "Summarise my recent food records.",
                                        "Compare the sources I attach.",
                                    ).forEach { prompt ->
                                        AssistChip(onClick = { onDraftChange(prompt) }, label = { Text(prompt) })
                                    }
                                }
                            }
                        }
                        items(state.messages, key = { it.id ?: "${it.createdAt}-${it.role}-${it.content}" }) { message ->
                            ChatMessageItem(message, onOpenSource, onDraftChange, onOpenHome)
                        }
                        if (state.hasMore) {
                            item {
                                OutlinedButton(
                                    onClick = onLoadMore,
                                    enabled = !state.isLoadingMore,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (state.isLoadingMore) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                                    Text("Load more messages")
                                }
                            }
                        }
                    }

                    state.errorMessage?.let { error ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(error, Modifier.weight(1f), color = FoodMindCoral, fontSize = 13.sp)
                            if (state.requiresLogin) TextButton(onClick = onLogin) { Text("Sign in") }
                        }
                    }

                    if (showSources) {
                        SourcePicker(
                            state = state,
                            onSearch = onSearch,
                            onFilter = onFilter,
                            onAttach = { item -> onAttach(item); showSources = false },
                            onClose = { showSources = false },
                        )
                    }

                    if (state.attachedReferences.isNotEmpty()) {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            state.attachedReferences.forEach { reference ->
                                AssistChip(
                                    onClick = { onRemoveReference(reference.id) },
                                    label = { Text(reference.title ?: reference.sourceType ?: "Source") },
                                    trailingIcon = { Icon(Icons.Outlined.Close, "Remove source") },
                                )
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().background(FoodMindSurface).padding(10.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        IconButton(onClick = { showSources = !showSources }) {
                            Icon(Icons.Outlined.AttachFile, "Attach authorised source")
                        }
                        OutlinedTextField(
                            value = state.draft,
                            onValueChange = onDraftChange,
                            placeholder = { Text("Ask about FoodMind content…") },
                            supportingText = { Text("${state.draft.length}/$CHAT_MESSAGE_LIMIT") },
                            modifier = Modifier.weight(1f),
                            maxLines = 4,
                        )
                        IconButton(
                            onClick = onSend,
                            enabled = state.draft.isNotBlank() && !state.isSending,
                        ) {
                            if (state.isSending) CircularProgressIndicator()
                            else Icon(Icons.AutoMirrored.Outlined.Send, "Send")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatLoadError(state: ChatUiState, onRetry: () -> Unit, onLogin: () -> Unit) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(state.errorMessage ?: "Could not open chat", color = FoodMindCoral)
        if (state.requiresLogin) OutlinedButton(onClick = onLogin) { Text("Sign in") }
        else OutlinedButton(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessageResponse,
    onOpenSource: (ChatMessageSourceResponse) -> Unit,
    onDraftChange: (String) -> Unit,
    onOpenHome: () -> Unit,
) {
    val isUser = message.role == "USER"
    val isOutOfScope = message.route in setOf("OUT_OF_SCOPE", "UNSUPPORTED")
    Column(
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = if (isUser) FoodMindGreenDark else FoodMindSurface),
            border = if (isUser) null else BorderStroke(1.dp, FoodMindLine),
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message.content.orEmpty(), color = if (isUser) Color.White else FoodMindInk)
                if (!isUser && (!message.route.isNullOrBlank() || !message.responseStatus.isNullOrBlank())) {
                    Text(
                        listOfNotNull(message.route?.toReadableLabel(), message.responseStatus?.toReadableLabel())
                            .joinToString(" · "),
                        color = FoodMindMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        val groundedSources = message.sources.filter {
            !it.sourceId.isNullOrBlank() && !it.sourceType.isNullOrBlank()
        }
        if (
            !isUser &&
            message.responseStatus == "SUCCEEDED" &&
            message.route in setOf("SEARCH", "SUMMARY", "COMPARE") &&
            groundedSources.isNotEmpty()
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 5.dp),
            ) {
                groundedSources.forEach { source ->
                    AssistChip(
                        onClick = { onOpenSource(source) },
                        enabled = source.sourceId != null && source.sourceType in setOf("FOOD_RECORD", "FOOD_PRODUCT", "PLACE"),
                        label = { Text(source.title ?: source.sourceType ?: "Source") },
                        trailingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open source") },
                    )
                }
            }
        }
        if (isOutOfScope) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(
                    onClick = { onDraftChange("Search my authorised FoodMind content for this topic.") },
                    label = { Text("Search my content") },
                )
                AssistChip(onClick = onOpenHome, label = { Text("Open FoodMind tools") })
            }
        }
    }
}

@Composable
private fun SourcePicker(
    state: ChatUiState,
    onSearch: (String) -> Unit,
    onFilter: (ChatSourceFilter) -> Unit,
    onAttach: (ExploreItemResponse) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(FoodMindSurface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search authorised FoodMind sources") },
            singleLine = true,
            trailingIcon = { IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close") } },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChatSourceFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.sourceFilter == filter,
                    onClick = { onFilter(filter) },
                    label = { Text(filter.label) },
                )
            }
        }
        when {
            state.isSearching -> CircularProgressIndicator()
            state.searchQuery.trim().length >= 2 && state.searchResults.isEmpty() ->
                Text("No accessible sources found.", color = FoodMindMuted, fontSize = 13.sp)
            else -> state.searchResults.take(8).forEach { result ->
                TextButton(onClick = { onAttach(result) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text(result.title ?: "Untitled")
                        Text(
                            listOfNotNull(result.sourceType, result.visibility).joinToString(" · "),
                            color = FoodMindMuted,
                            fontSize = 12.sp,
                        )
                    }
                    Icon(Icons.Outlined.Add, "Attach")
                }
            }
        }
    }
}

private fun String.toReadableLabel(): String =
    lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
