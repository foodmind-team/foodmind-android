package com.foodmind.foodmind_android

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.ChatMessageResponse
import com.foodmind.foodmind_android.core.network.ChatMessageSourceResponse
import com.foodmind.foodmind_android.core.network.ChatReferenceResponse
import com.foodmind.foodmind_android.core.network.ChatSessionResponse
import com.foodmind.foodmind_android.core.network.ExploreItemResponse
import com.foodmind.foodmind_android.feature.chat.CHAT_MESSAGE_LIMIT
import com.foodmind.foodmind_android.feature.chat.CHAT_STARTER_PROMPTS
import com.foodmind.foodmind_android.feature.chat.ChatDestination
import com.foodmind.foodmind_android.feature.chat.ChatListUiState
import com.foodmind.foodmind_android.feature.chat.ChatSourceFilter
import com.foodmind.foodmind_android.feature.chat.ChatUiState
import com.foodmind.foodmind_android.feature.chat.OutgoingChatMessage
import com.foodmind.foodmind_android.feature.chat.OutgoingMessageStatus
import com.foodmind.foodmind_android.feature.chat.chatAnswerMode
import com.foodmind.foodmind_android.feature.chat.chatSourceTypeLabel
import com.foodmind.foodmind_android.feature.chat.conversationSources
import com.foodmind.foodmind_android.feature.chat.isGroundedChatAnswer
import com.foodmind.foodmind_android.feature.chat.normaliseChatSourceType
import com.foodmind.foodmind_android.feature.chat.quickActionsFor
import com.foodmind.foodmind_android.feature.chat.suggestedQuestionsFor
import com.foodmind.foodmind_android.feature.chat.visibleSessions

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnhancedChatScreen(
    state: ChatUiState,
    onBack: () -> Unit = {},
    onRetryLoad: () -> Unit = {},
    onDraftChange: (String) -> Unit = {},
    onSend: () -> Unit = {},
    onRetryMessage: () -> Unit = {},
    onEditMessage: () -> Unit = {},
    onDismissMessage: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onSearch: (String) -> Unit = {},
    onFilter: (ChatSourceFilter) -> Unit = {},
    onAttach: (ExploreItemResponse) -> Unit = {},
    onReattach: (ChatMessageSourceResponse) -> Unit = {},
    onRemoveReference: (String?) -> Unit = {},
    onClearReferences: () -> Unit = {},
    onOpenSource: (ChatMessageSourceResponse) -> Unit = {},
    onCopyAnswer: (String) -> Unit = {},
    onShareAnswer: (ChatMessageResponse) -> Unit = {},
    onDestination: (ChatDestination) -> Unit = {},
    onLogin: () -> Unit = {},
) {
    var showSourcePicker by remember { mutableStateOf(false) }
    var showContext by remember { mutableStateOf(false) }
    val historicalSources = state.conversationSources()

    FoodMindDetailScaffold(state.title, onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .testTag("chat_screen"),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.padding(24.dp))
                state.sessionId == null -> EnhancedChatLoadError(state, onRetryLoad, onLogin)
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            FoodMindSurfaceCard {
                                Column(
                                    Modifier.testTag("chat_boundary_card"),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text("Read-only FoodMind assistant", fontWeight = FontWeight.Bold)
                                    Text(
                                        "It can use authorised FoodMind sources or general knowledge. " +
                                            "It never changes data or executes recommendation or cooking actions. " +
                                            "Source cards appear only for grounded answers.",
                                        color = FoodMindMuted,
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                        item {
                            OutlinedButton(
                                onClick = { showContext = !showContext },
                                modifier = Modifier.fillMaxWidth().testTag("source_context_toggle"),
                            ) {
                                Text(
                                    "Sources: ${state.attachedReferences.size} this turn, " +
                                        "${historicalSources.size} cited earlier",
                                    Modifier.weight(1f),
                                )
                                Icon(
                                    if (showContext) Icons.Outlined.Clear else Icons.Outlined.AttachFile,
                                    if (showContext) "Hide source context" else "Show source context",
                                )
                            }
                        }
                        if (showContext) {
                            item {
                                ConversationSourceContext(
                                    current = state.attachedReferences,
                                    historical = historicalSources,
                                    onRemoveReference = onRemoveReference,
                                    onClearReferences = onClearReferences,
                                    onOpenSource = onOpenSource,
                                    onReattach = onReattach,
                                )
                            }
                        }
                        if (state.messages.isEmpty() && state.outgoingMessage == null) {
                            item {
                                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Ask a read-only question or attach a FoodMind source.", color = FoodMindMuted)
                                    CHAT_STARTER_PROMPTS.forEach { prompt ->
                                        AssistChip(onClick = { onDraftChange(prompt) }, label = { Text(prompt) })
                                    }
                                }
                            }
                        }
                        items(
                            state.messages,
                            key = { it.id ?: "${it.createdAt}-${it.role}-${it.content}" },
                        ) { message ->
                            EnhancedChatMessageItem(
                                message,
                                onOpenSource,
                                onDraftChange,
                                onCopyAnswer,
                                onShareAnswer,
                                onDestination,
                            )
                        }
                        state.outgoingMessage?.let { outgoing ->
                            item(key = outgoing.localId) {
                                OutgoingMessageCard(outgoing, onRetryMessage, onEditMessage, onDismissMessage)
                            }
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
                    if (showSourcePicker) {
                        EnhancedSourcePicker(state, onSearch, onFilter, onAttach) {
                            showSourcePicker = false
                        }
                    }
                    CurrentTurnSources(state.attachedReferences, onRemoveReference, onClearReferences)
                    Column(
                        Modifier.fillMaxWidth().background(FoodMindSurface).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            if (state.attachedReferences.isEmpty()) {
                                "This message will not reuse sources from earlier turns."
                            } else {
                                "This message will use only ${state.attachedReferences.size} selected source(s)."
                            },
                            color = FoodMindMuted,
                            fontSize = 11.sp,
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            IconButton(
                                onClick = { showSourcePicker = !showSourcePicker },
                                enabled = !state.isSending,
                            ) { Icon(Icons.Outlined.AttachFile, "Attach authorised source") }
                            OutlinedTextField(
                                value = state.draft,
                                onValueChange = onDraftChange,
                                placeholder = { Text("Ask a read-only question...") },
                                supportingText = { Text("${state.draft.length}/$CHAT_MESSAGE_LIMIT") },
                                modifier = Modifier.weight(1f).testTag("chat_composer"),
                                enabled = !state.isSending,
                                maxLines = 4,
                            )
                            IconButton(
                                onClick = onSend,
                                enabled = state.draft.isNotBlank() && !state.isSending,
                                modifier = Modifier.testTag("send_message"),
                            ) {
                                if (state.isSending) CircularProgressIndicator()
                                else Icon(Icons.AutoMirrored.Outlined.Send, "Send message")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnhancedChatLoadError(state: ChatUiState, onRetry: () -> Unit, onLogin: () -> Unit) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(state.errorMessage ?: "Could not open chat", color = FoodMindCoral)
        if (state.requiresLogin) OutlinedButton(onClick = onLogin) { Text("Sign in") }
        else OutlinedButton(onClick = onRetry) { Text("Try again") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnhancedChatMessageItem(
    message: ChatMessageResponse,
    onOpenSource: (ChatMessageSourceResponse) -> Unit,
    onDraftChange: (String) -> Unit,
    onCopyAnswer: (String) -> Unit,
    onShareAnswer: (ChatMessageResponse) -> Unit,
    onDestination: (ChatDestination) -> Unit,
) {
    val isUser = message.role == "USER"
    val answerMode = chatAnswerMode(message)
    val groundedSources = message.sources.filter {
        !it.sourceId.isNullOrBlank() && !it.sourceType.isNullOrBlank()
    }
    Column(
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) FoodMindGreenDark else FoodMindSurface,
            ),
            border = if (isUser) null else BorderStroke(1.dp, FoodMindLine),
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                answerMode?.let { mode ->
                    Text(
                        mode.label,
                        color = if (mode.name == "SCOPE_BOUNDARY") FoodMindCoral else FoodMindGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("answer_mode_${mode.name.lowercase()}"),
                    )
                }
                Text(message.content.orEmpty(), color = if (isUser) Color.White else FoodMindInk)
                if (!isUser && (!message.route.isNullOrBlank() || !message.responseStatus.isNullOrBlank())) {
                    Text(
                        listOfNotNull(
                            message.route?.readableChatLabel(),
                            message.responseStatus?.readableChatLabel(),
                        ).joinToString(" | "),
                        color = FoodMindMuted,
                        fontSize = 11.sp,
                    )
                }
                if (!isUser) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onCopyAnswer(message.content.orEmpty()) }) {
                            Icon(Icons.Outlined.ContentCopy, "Copy answer")
                            Text("Copy", Modifier.padding(start = 4.dp))
                        }
                        TextButton(onClick = { onShareAnswer(message) }) {
                            Icon(Icons.Outlined.Share, "Share answer")
                            Text("Share", Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }
        if (isGroundedChatAnswer(message) && groundedSources.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 5.dp).testTag("grounded_sources"),
            ) {
                groundedSources.forEach { source ->
                    AssistChip(
                        onClick = { onOpenSource(source) },
                        label = {
                            Column {
                                Text(source.title ?: chatSourceTypeLabel(source.sourceType))
                                source.snippet?.takeIf(String::isNotBlank)?.let {
                                    Text(it, color = FoodMindMuted, fontSize = 10.sp, maxLines = 1)
                                }
                            }
                        },
                        trailingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open source") },
                    )
                }
            }
        }
        val quickActions = quickActionsFor(message)
        val suggestedQuestions = suggestedQuestionsFor(message)
        if (message.route in setOf("OUT_OF_SCOPE", "UNSUPPORTED") ||
            quickActions.isNotEmpty() || suggestedQuestions.isNotEmpty()
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                suggestedQuestions.forEachIndexed { index, prompt ->
                    AssistChip(
                        onClick = { onDraftChange(prompt) },
                        label = { Text(prompt) },
                        modifier = Modifier.testTag("suggested_question_$index"),
                    )
                }
                if (message.route in setOf("OUT_OF_SCOPE", "UNSUPPORTED") && suggestedQuestions.isEmpty()) {
                    AssistChip(
                        onClick = { onDraftChange("Search my authorised FoodMind content for this topic.") },
                        label = { Text("Search my content") },
                    )
                }
                quickActions.forEach { destination ->
                    AssistChip(
                        onClick = { onDestination(destination) },
                        label = { Text(destination.label) },
                        modifier = Modifier.testTag("quick_action_${destination.name.lowercase()}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun OutgoingMessageCard(
    outgoing: OutgoingChatMessage,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.fillMaxWidth().testTag(
            if (outgoing.status == OutgoingMessageStatus.FAILED) "outgoing_failed" else "outgoing_sending",
        ),
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = FoodMindGreenDark),
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(outgoing.content, color = Color.White)
                if (outgoing.referenceTitles.isNotEmpty()) {
                    Text(
                        "Sources: ${outgoing.referenceTitles.joinToString()}",
                        color = FoodMindMuted,
                        fontSize = 11.sp,
                    )
                }
                Text(
                    if (outgoing.status == OutgoingMessageStatus.FAILED) "Not sent" else "Sending...",
                    color = if (outgoing.status == OutgoingMessageStatus.FAILED) FoodMindCoral else FoodMindMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (outgoing.status == OutgoingMessageStatus.FAILED) {
            Row {
                TextButton(onClick = onRetry, modifier = Modifier.testTag("retry_failed")) {
                    Icon(Icons.Outlined.Refresh, "Retry failed message")
                    Text("Retry")
                }
                TextButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, "Edit failed message")
                    Text("Edit")
                }
                TextButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, "Dismiss failed message")
                    Text("Dismiss")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrentTurnSources(
    references: List<ChatReferenceResponse>,
    onRemoveReference: (String?) -> Unit,
    onClearReferences: () -> Unit,
) {
    if (references.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Selected for this turn", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            TextButton(onClick = onClearReferences) { Text("Clear") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            references.forEach { reference ->
                AssistChip(
                    onClick = { onRemoveReference(reference.id) },
                    label = { Text(reference.title ?: chatSourceTypeLabel(reference.sourceType)) },
                    trailingIcon = { Icon(Icons.Outlined.Close, "Remove source") },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConversationSourceContext(
    current: List<ChatReferenceResponse>,
    historical: List<ChatMessageSourceResponse>,
    onRemoveReference: (String?) -> Unit,
    onClearReferences: () -> Unit,
    onOpenSource: (ChatMessageSourceResponse) -> Unit,
    onReattach: (ChatMessageSourceResponse) -> Unit,
) {
    FoodMindSurfaceCard {
        Column(Modifier.testTag("source_context_panel"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Current turn", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                if (current.isNotEmpty()) TextButton(onClick = onClearReferences) { Text("Clear") }
            }
            if (current.isEmpty()) {
                Text("No source selected. Earlier sources will not be reused.", color = FoodMindMuted)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    current.forEach { reference ->
                        AssistChip(
                            onClick = { onRemoveReference(reference.id) },
                            label = { Text(reference.title ?: chatSourceTypeLabel(reference.sourceType)) },
                            trailingIcon = { Icon(Icons.Outlined.Close, "Remove source") },
                        )
                    }
                }
            }
            Text("Cited earlier", fontWeight = FontWeight.Bold)
            if (historical.isEmpty()) {
                Text("No grounded source has been cited in this conversation.", color = FoodMindMuted)
            } else {
                historical.forEach { source ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = FoodMindSurfaceRaised),
                        border = BorderStroke(1.dp, FoodMindLineSoft),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(source.title ?: chatSourceTypeLabel(source.sourceType))
                                Text(chatSourceTypeLabel(source.sourceType), color = FoodMindMuted, fontSize = 11.sp)
                            }
                            IconButton(onClick = { onOpenSource(source) }) {
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open cited source")
                            }
                            TextButton(onClick = { onReattach(source) }) { Text("Use this turn") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnhancedSourcePicker(
    state: ChatUiState,
    onSearch: (String) -> Unit,
    onFilter: (ChatSourceFilter) -> Unit,
    onAttach: (ExploreItemResponse) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(FoodMindSurface).padding(12.dp).testTag("source_picker"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search authorised FoodMind sources") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close source picker") } },
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
            else -> LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    state.searchResults.take(8),
                    key = { "${it.sourceType}:${it.sourceId}" },
                ) { result ->
                    val normalisedType = result.sourceType?.let(::normaliseChatSourceType)
                    val selected = state.attachedReferences.any {
                        it.sourceType == normalisedType && it.sourceId == result.sourceId
                    }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) FoodMindSurfaceRaised else FoodMindSurface,
                        ),
                        border = BorderStroke(1.dp, if (selected) FoodMindGreen else FoodMindLine),
                        modifier = Modifier.testTag("source_result_${result.sourceId.orEmpty()}"),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(result.title ?: "Untitled", fontWeight = FontWeight.Bold)
                                listOfNotNull(
                                    chatSourceTypeLabel(result.sourceType).takeIf(String::isNotBlank),
                                    result.visibility?.readableChatLabel(),
                                    result.occurredAt?.let(::formatFoodMindTimestamp)?.takeIf(String::isNotBlank),
                                ).takeIf(List<String>::isNotEmpty)?.let {
                                    Text(it.joinToString(" | "), color = FoodMindMuted, fontSize = 11.sp)
                                }
                                result.subtitle?.takeIf(String::isNotBlank)?.let {
                                    Text(it, color = FoodMindGreen, fontSize = 12.sp)
                                }
                                result.snippet?.takeIf(String::isNotBlank)?.let {
                                    Text(it, color = FoodMindMuted, fontSize = 12.sp, maxLines = 2)
                                }
                            }
                            TextButton(onClick = { onAttach(result) }, enabled = !selected) {
                                Icon(
                                    if (selected) Icons.Outlined.Clear else Icons.Outlined.Add,
                                    if (selected) "Already selected" else "Attach source",
                                )
                                Text(if (selected) "Selected" else "Add")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EnhancedChatListScreen(
    state: ChatListUiState,
    onBack: () -> Unit = {},
    onOpen: (String?, String?) -> Unit = { _, _ -> },
    onRefresh: () -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onRequestArchive: (ChatSessionResponse) -> Unit = {},
    onDismissArchive: () -> Unit = {},
    onConfirmArchive: () -> Unit = {},
) {
    state.archiveCandidate?.let { session ->
        AlertDialog(
            onDismissRequest = onDismissArchive,
            title = { Text("Archive conversation?") },
            text = { Text("\"${session.title ?: "FoodMind Chat"}\" will be removed from this list.") },
            confirmButton = {
                TextButton(onClick = onConfirmArchive, enabled = !state.isArchiving) {
                    Text(if (state.isArchiving) "Archiving..." else "Archive")
                }
            },
            dismissButton = { TextButton(onClick = onDismissArchive) { Text("Cancel") } },
        )
    }
    val visibleSessions = state.visibleSessions()
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
                Modifier.fillMaxSize().padding(padding).testTag("chat_list_screen"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    FoodMindSurfaceCard {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Read-only FoodMind assistant", fontWeight = FontWeight.Bold)
                            Text(
                                "Ask about authorised FoodMind content or general knowledge. " +
                                    "Chat never changes data or executes recommendation and cooking actions.",
                                color = FoodMindMuted,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                item {
                    Text("Start with a prompt", fontWeight = FontWeight.ExtraBold)
                    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth().testTag("conversation_search"),
                        label = { Text("Search conversations") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Outlined.Close, "Clear conversation search")
                                }
                            }
                        },
                    )
                }
                if (visibleSessions.isEmpty() && state.errorMessage == null) {
                    item {
                        FoodMindSurfaceCard {
                            Text(
                                if (state.query.isBlank()) {
                                    "No conversations yet. Choose a prompt or use + to start one."
                                } else {
                                    "No conversation matches \"${state.query}\"."
                                },
                            )
                        }
                    }
                }
                items(visibleSessions, key = { it.id.orEmpty() }) { session ->
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
                                        session.status?.readableChatLabel(),
                                        formatFoodMindTimestamp(session.updatedAt),
                                    ).filter(String::isNotBlank).joinToString(" | "),
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

private fun String.readableChatLabel(): String =
    lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

