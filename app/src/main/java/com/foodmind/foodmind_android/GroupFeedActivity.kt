package com.foodmind.foodmind_android

import android.content.Context
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
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.CreateInvitationRequest
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.GroupFeedItemResponse
import com.foodmind.foodmind_android.core.network.GroupMemberResponse
import com.foodmind.foodmind_android.core.network.GroupResponse
import com.foodmind.foodmind_android.core.network.UpdateGroupRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class GroupWorkspaceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_GROUP_ID).orEmpty(); val client = foodMindApiClient()
        setContent { FoodMindTheme { GroupWorkspaceScreen(client, id, ::finish) { type, recordId -> startActivity(RecordDetailActivity.intent(this, type, recordId)) } } }
    }
    companion object { private const val EXTRA_GROUP_ID = "group_id"; fun intent(context: Context, groupId: String) = Intent(context, GroupWorkspaceActivity::class.java).putExtra(EXTRA_GROUP_ID, groupId) }
}

/** Compatibility entry point retained for existing intents. */
class GroupFeedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); startActivity(GroupWorkspaceActivity.intent(this, intent.getStringExtra("group_id").orEmpty())); finish() }
}

@Composable
private fun GroupWorkspaceScreen(client: FoodMindApiClient, groupId: String, onBack: () -> Unit, onOpenRecord: (String, String) -> Unit) {
    var group by remember { mutableStateOf<GroupResponse?>(null) }; var members by remember { mutableStateOf<List<GroupMemberResponse>>(emptyList()) }; var feed by remember { mutableStateOf<List<GroupFeedItemResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf<String?>(null) }; var tab by remember { mutableIntStateOf(0) }; var editMode by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }; var inviteToken by remember { mutableStateOf<String?>(null) }; var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope(); val clipboard = LocalClipboardManager.current
    LaunchedEffect(groupId, refresh) {
        loading = true
        runCatching { coroutineScope { val g = async { client.group(groupId) }; val m = async { client.groupMembers(groupId) }; val f = async { client.groupFeed(groupId) }; Triple(g.await(), m.await(), f.await().items) } }
            .onSuccess { (g, m, f) -> group = g; members = m; feed = f; name = g.name.orEmpty(); description = g.description.orEmpty(); error = null }.onFailure { error = "群组工作区加载失败。" }
        loading = false
    }
    FoodMindDetailScaffold(group?.name ?: "群组", onBack, actions = {
        IconButton(onClick = { editMode = !editMode }) { Icon(Icons.Outlined.Edit, "编辑群组") }
        IconButton(onClick = { scope.launch { runCatching { client.createGroupInvitation(groupId, CreateInvitationRequest(expiresInHours = 24, maxUses = 10)) }.onSuccess { inviteToken = it.token }.onFailure { error = "邀请创建失败。" } } }) { Icon(Icons.Outlined.PersonAdd, "创建邀请") }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading) CircularProgressIndicator(Modifier.padding(24.dp))
            error?.let { Text(it, Modifier.padding(16.dp), color = FoodMindCoral) }
            group?.let { current ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(current.description ?: "共享饮食决定", color = FoodMindMuted)
                    Text("${members.size} 位成员 · ${current.status}", color = FoodMindGreen, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                    if (editMode) {
                        OutlinedTextField(name, { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)); OutlinedTextField(description, { description = it }, label = { Text("描述") }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { scope.launch { runCatching { client.updateGroup(groupId, UpdateGroupRequest(name.trim(), description.trim())) }.onSuccess { editMode = false; refresh++ }.onFailure { error = "保存失败。" } } }) { Text("保存") }
                            OutlinedButton(onClick = { scope.launch { runCatching { client.updateGroup(groupId, UpdateGroupRequest(status = "ARCHIVED")) }.onSuccess { onBack() } } }) { Icon(Icons.Outlined.Archive, null); Text("归档") }
                        }
                    }
                    inviteToken?.let { token -> Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF7F0)), modifier = Modifier.padding(top = 10.dp)) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("邀请令牌", fontWeight = FontWeight.Bold); Text(token) }; IconButton(onClick = { clipboard.setText(AnnotatedString(token)) }) { Icon(Icons.Outlined.ContentCopy, "复制") } } } }
                }
                PrimaryTabRow(tab) { Tab(tab == 0, { tab = 0 }, text = { Text("动态") }); Tab(tab == 1, { tab = 1 }, text = { Text("成员") }) }
                if (tab == 0) LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (feed.isEmpty()) item { FoodMindSurfaceCard { Text("还没有共享动态。") } }
                    items(feed, key = { it.sourceId.orEmpty() }) { item -> Card(onClick = { item.foodRecordId?.let { onOpenRecord("FOOD", it) } }, colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, FoodMindLine)) { Column(Modifier.fillMaxWidth().padding(15.dp)) { Text(item.actorDisplayName ?: "群组成员", fontWeight = FontWeight.Bold); Text(item.mealNameSnapshot ?: item.message ?: "共享了一项饮食决定", modifier = Modifier.padding(top = 5.dp)); Text(item.occurredAt.orEmpty(), color = FoodMindMuted, fontSize = 11.sp) } } }
                } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(members, key = { it.userId.orEmpty() }) { member -> Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, FoodMindLine)) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { FoodMindAvatar(member.displayName ?: "人"); Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(member.displayName ?: "成员", fontWeight = FontWeight.Bold); Text(member.role ?: "MEMBER", color = FoodMindMuted) }; IconButton(onClick = { member.userId?.let { userId -> scope.launch { runCatching { client.removeGroupMember(groupId, userId) }.onSuccess { members = members.filterNot { it.userId == userId } }.onFailure { error = "移除成员失败。" } } } }) { Icon(Icons.Outlined.DeleteOutline, "移除") } } } }
                }
            }
        }
    }
}
