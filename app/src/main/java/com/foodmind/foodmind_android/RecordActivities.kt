package com.foodmind.foodmind_android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.CreateDrinkRecordRequest
import com.foodmind.foodmind_android.core.network.CreateFoodRecordRequest
import com.foodmind.foodmind_android.core.network.DrinkRecordResponse
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.FoodRecordResponse
import com.foodmind.foodmind_android.core.network.UpdateDrinkRecordRequest
import com.foodmind.foodmind_android.core.network.UpdateFoodRecordRequest
import com.foodmind.foodmind_android.domain.repository.MediaUploadRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

class RecordCollectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val client = foodMindApiClient()
        setContent {
            FoodMindTheme {
                RecordCollectionScreen(
                    client, ::finish,
                    onAdd = { startActivity(RecordEditorActivity.intent(this, it, null)) },
                    onOpen = { type, id -> startActivity(RecordDetailActivity.intent(this, type, id)) },
                )
            }
        }
    }
}

@Composable
private fun RecordCollectionScreen(client: FoodMindApiClient, onBack: () -> Unit, onAdd: (String) -> Unit, onOpen: (String, String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var food by remember { mutableStateOf<List<FoodRecordResponse>>(emptyList()) }
    var drinks by remember { mutableStateOf<List<DrinkRecordResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        loading = true
        runCatching { coroutineScope { val foodCall = async { client.foodRecords().items }; val drinkCall = async { client.drinkRecords().items }; foodCall.await() to drinkCall.await() } }
            .onSuccess { food = it.first; drinks = it.second; error = null }.onFailure { error = "记录加载失败。" }
        loading = false
    }
    FoodMindDetailScaffold("记录", onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("餐食 ${food.size}") })
                Tab(tab == 1, { tab = 1 }, text = { Text("饮品 ${drinks.size}") })
            }
            when {
                loading -> CircularProgressIndicator(Modifier.padding(24.dp))
                error != null -> Column(Modifier.padding(24.dp)) { Text(error!!, color = FoodMindCoral); TextButton(onClick = { refresh++ }) { Text("重试") } }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Button(onClick = { onAdd(if (tab == 0) "FOOD" else "DRINK") }, modifier = Modifier.fillMaxWidth()) { Text(if (tab == 0) "记录一餐" else "记录一杯饮品") } }
                    if (tab == 0) items(food, key = FoodRecordResponse::id) { record -> RecordListCard(record.mealNameSnapshot, record.placeNameSnapshot, record.occurredAt, record.rating) { onOpen("FOOD", record.id) } }
                    else items(drinks, key = DrinkRecordResponse::id) { record -> RecordListCard(record.drinkName, record.shopNameSnapshot, record.occurredAt, record.rating) { onOpen("DRINK", record.id) } }
                }
            }
        }
    }
}

@Composable
private fun RecordListCard(title: String, context: String?, occurredAt: String, rating: Double?, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, FoodMindLine)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(listOfNotNull(context, rating?.let { "评分 $it" }).joinToString(" · "), color = FoodMindMuted, modifier = Modifier.padding(top = 4.dp))
            Text(occurredAt, color = FoodMindMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

private data class RecordFormSeed(
    val name: String = "", val place: String = "", val occurredAt: String = Instant.now().toString(),
    val price: String = "", val currency: String = "SGD", val rating: String = "",
    val comment: String = "", val visibility: String = "PRIVATE", val repeat: Boolean? = null,
    val sweetness: String = "", val ice: String = "", val groupId: String = "", val mediaAssetId: String? = null,
    val version: Long = 0,
)

class RecordEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = intent.getStringExtra(EXTRA_TYPE) ?: "FOOD"
        val id = intent.getStringExtra(EXTRA_ID)
        val client = foodMindApiClient()
        setContent { FoodMindTheme { RecordEditorScreen(client, type, id, ::finish, contentResolver) } }
    }
    companion object {
        private const val EXTRA_TYPE = "record_type"; private const val EXTRA_ID = "record_id"
        fun intent(context: Context, type: String, id: String?) = Intent(context, RecordEditorActivity::class.java).putExtra(EXTRA_TYPE, type).apply { id?.let { putExtra(EXTRA_ID, it) } }
    }
}

@Composable
private fun RecordEditorScreen(
    client: FoodMindApiClient,
    type: String,
    id: String?,
    onBack: () -> Unit,
    contentResolver: android.content.ContentResolver,
) {
    var seed by remember { mutableStateOf<RecordFormSeed?>(if (id == null) RecordFormSeed() else null) }
    var name by remember { mutableStateOf("") }; var place by remember { mutableStateOf("") }; var occurredAt by remember { mutableStateOf(Instant.now().toString()) }
    var price by remember { mutableStateOf("") }; var currency by remember { mutableStateOf("SGD") }; var rating by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }; var visibility by remember { mutableStateOf("PRIVATE") }; var repeat by remember { mutableStateOf<Boolean?>(null) }
    var sweetness by remember { mutableStateOf("") }; var ice by remember { mutableStateOf("") }; var groupId by remember { mutableStateOf("") }
    var mediaAssetId by remember { mutableStateOf<String?>(null) }; var selectedImage by remember { mutableStateOf<Uri?>(null) }
    var originalMediaAssetId by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedImage = it }
    LaunchedEffect(id) {
        if (id != null) runCatching {
            if (type == "DRINK") client.drinkRecord(id).let { RecordFormSeed(it.drinkName, it.shopNameSnapshot, it.occurredAt, it.price?.amount?.toString().orEmpty(), it.price?.currency ?: "SGD", it.rating?.toString().orEmpty(), it.comment.orEmpty(), it.visibility, it.wouldBuyAgain, it.sweetnessLevel?.toString().orEmpty(), it.iceLevel?.toString().orEmpty(), it.groupId.orEmpty(), it.mediaAssetId, it.version) }
            else client.foodRecord(id).let { RecordFormSeed(it.mealNameSnapshot, it.placeNameSnapshot.orEmpty(), it.occurredAt, it.price?.amount?.toString().orEmpty(), it.price?.currency ?: "SGD", it.rating?.toString().orEmpty(), it.comment.orEmpty(), it.visibility, it.wouldEatAgain, groupId = it.groupId.orEmpty(), mediaAssetId = it.mediaAssetId, version = it.version) }
        }.onSuccess { loaded -> seed = loaded; name = loaded.name; place = loaded.place; occurredAt = loaded.occurredAt; price = loaded.price; currency = loaded.currency; rating = loaded.rating; comment = loaded.comment; visibility = loaded.visibility; repeat = loaded.repeat; sweetness = loaded.sweetness; ice = loaded.ice; groupId = loaded.groupId; mediaAssetId = loaded.mediaAssetId; originalMediaAssetId = loaded.mediaAssetId; version = loaded.version }
            .onFailure { error = "记录加载失败。" }
    }
    FoodMindDetailScaffold(if (id == null) "新增记录" else "编辑记录", onBack) { padding ->
        if (seed == null && error == null) CircularProgressIndicator(Modifier.padding(padding).padding(24.dp)) else LazyColumn(
            Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            item { Text(if (type == "DRINK") "记录一杯饮品" else "记录一餐", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold); Text("保存到当前后端账号。", color = FoodMindMuted) }
            item { OutlinedTextField(name, { name = it }, label = { Text(if (type == "DRINK") "饮品名称" else "餐食名称") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(place, { place = it }, label = { Text(if (type == "DRINK") "店铺" else "地点") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(occurredAt, { occurredAt = it }, label = { Text("发生时间（ISO 8601）") }, modifier = Modifier.fillMaxWidth()) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(price, { price = it }, label = { Text("价格") }, modifier = Modifier.weight(1f)); OutlinedTextField(currency, { currency = it.uppercase().take(3) }, label = { Text("币种") }, modifier = Modifier.weight(1f)) } }
            item { OutlinedTextField(rating, { rating = it }, label = { Text("评分") }, modifier = Modifier.fillMaxWidth()) }
            if (type == "DRINK") item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(sweetness, { sweetness = it.filter(Char::isDigit) }, label = { Text("甜度") }, modifier = Modifier.weight(1f)); OutlinedTextField(ice, { ice = it.filter(Char::isDigit) }, label = { Text("冰量") }, modifier = Modifier.weight(1f)) } }
            item { OutlinedTextField(comment, { comment = it }, label = { Text("评论") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
            item { Text("可见范围", fontWeight = FontWeight.Bold); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("PRIVATE", "GROUP").forEach { FilterChip(visibility == it, { visibility = it }, label = { Text(if (it == "PRIVATE") "仅自己" else "群组") }) } }; if (visibility == "GROUP") OutlinedTextField(groupId, { groupId = it }, label = { Text("群组 ID") }, modifier = Modifier.fillMaxWidth()) }
            item { Text(if (type == "DRINK") "会再次购买？" else "会再次吃？", fontWeight = FontWeight.Bold); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(true to "会", false to "不会").forEach { (value, label) -> FilterChip(repeat == value, { repeat = value }, label = { Text(label) }) } } }
            item {
                OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.AddAPhoto, null); Text(if (selectedImage != null) "已选择新图片" else if (mediaAssetId != null) "替换已上传图片" else "添加安全图片", modifier = Modifier.padding(start = 8.dp)) }
                if (mediaAssetId != null || selectedImage != null) TextButton(onClick = { selectedImage = null; mediaAssetId = null }) { Text("移除图片") }
            }
            item {
                error?.let { Text(it, color = FoodMindCoral) }
                Button(
                    onClick = { scope.launch {
                        if (name.isBlank()) { error = "请填写名称。"; return@launch }
                        saving = true; error = null
                        var newlyUploadedId: String? = null
                        runCatching {
                            val uploadedId = selectedImage?.let { MediaUploadRepository(client).upload(contentResolver, it).also { id -> newlyUploadedId = id } } ?: mediaAssetId
                            if (type == "DRINK") {
                                val request = if (id == null) CreateDrinkRecordRequest(name.trim(), shopNameSnapshot = place.trim(), occurredAt = occurredAt.trim(), price = price.toDoubleOrNull(), currency = currency, rating = rating.toDoubleOrNull(), comment = comment.ifBlank { null }, sweetnessLevel = sweetness.toIntOrNull(), iceLevel = ice.toIntOrNull(), wouldBuyAgain = repeat, visibility = visibility, groupId = groupId.ifBlank { null }, mediaAssetId = uploadedId)
                                else UpdateDrinkRecordRequest(drinkName = name.trim(), shopNameSnapshot = place.trim(), occurredAt = occurredAt.trim(), price = price.toDoubleOrNull(), currency = currency, rating = rating.toDoubleOrNull(), comment = comment.ifBlank { null }, sweetnessLevel = sweetness.toIntOrNull(), iceLevel = ice.toIntOrNull(), wouldBuyAgain = repeat, visibility = visibility, groupId = groupId.ifBlank { null }, mediaAssetId = uploadedId)
                                if (id == null) client.createDrinkRecord(request as CreateDrinkRecordRequest) else client.updateDrinkRecord(id, version, request as UpdateDrinkRecordRequest)
                            } else {
                                val request = if (id == null) CreateFoodRecordRequest(mealNameSnapshot = name.trim(), placeNameSnapshot = place.trim().ifBlank { null }, occurredAt = occurredAt.trim(), price = price.toDoubleOrNull(), currency = currency, rating = rating.toDoubleOrNull(), comment = comment.ifBlank { null }, wouldEatAgain = repeat, visibility = visibility, groupId = groupId.ifBlank { null }, mediaAssetId = uploadedId)
                                else UpdateFoodRecordRequest(mealNameSnapshot = name.trim(), placeNameSnapshot = place.trim().ifBlank { null }, occurredAt = occurredAt.trim(), price = price.toDoubleOrNull(), currency = currency, rating = rating.toDoubleOrNull(), comment = comment.ifBlank { null }, wouldEatAgain = repeat, visibility = visibility, groupId = groupId.ifBlank { null }, mediaAssetId = uploadedId)
                                if (id == null) client.createFoodRecord(request as CreateFoodRecordRequest) else client.updateFoodRecord(id, version, request as UpdateFoodRecordRequest)
                            }
                            originalMediaAssetId?.takeIf { it != uploadedId }?.let { runCatching { client.deleteMediaAsset(it) } }
                        }.onSuccess { onBack() }.onFailure {
                            newlyUploadedId?.let { uploaded -> runCatching { client.deleteMediaAsset(uploaded) } }
                            error = it.message ?: "保存失败，请检查输入。"
                        }
                        saving = false
                    } }, enabled = !saving, modifier = Modifier.fillMaxWidth(),
                ) { Text(if (saving) "保存中…" else "保存记录") }
            }
        }
    }
}

class RecordDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = intent.getStringExtra(EXTRA_TYPE) ?: "FOOD"; val id = intent.getStringExtra(EXTRA_ID).orEmpty(); val client = foodMindApiClient()
        setContent { FoodMindTheme { RecordDetailScreen(client, type, id, ::finish, { startActivity(RecordEditorActivity.intent(this, type, id)) }, { finish() }) } }
    }
    companion object {
        private const val EXTRA_TYPE = "record_type"; private const val EXTRA_ID = "record_id"
        fun intent(context: Context, type: String, id: String) = Intent(context, RecordDetailActivity::class.java).putExtra(EXTRA_TYPE, type).putExtra(EXTRA_ID, id)
    }
}

@Composable
private fun RecordDetailScreen(client: FoodMindApiClient, type: String, id: String, onBack: () -> Unit, onEdit: () -> Unit, onDeleted: () -> Unit) {
    var seed by remember { mutableStateOf<RecordFormSeed?>(null) }; var error by remember { mutableStateOf<String?>(null) }; var refresh by remember { mutableIntStateOf(0) }; val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    LaunchedEffect(id, refresh) { runCatching { if (type == "DRINK") client.drinkRecord(id).let { RecordFormSeed(it.drinkName, it.shopNameSnapshot, it.occurredAt, it.price?.amount?.toString().orEmpty(), it.price?.currency ?: "", it.rating?.toString().orEmpty(), it.comment.orEmpty(), it.visibility, it.wouldBuyAgain, it.sweetnessLevel?.toString().orEmpty(), it.iceLevel?.toString().orEmpty(), it.groupId.orEmpty(), it.mediaAssetId, it.version) } else client.foodRecord(id).let { RecordFormSeed(it.mealNameSnapshot, it.placeNameSnapshot.orEmpty(), it.occurredAt, it.price?.amount?.toString().orEmpty(), it.price?.currency ?: "", it.rating?.toString().orEmpty(), it.comment.orEmpty(), it.visibility, it.wouldEatAgain, groupId = it.groupId.orEmpty(), mediaAssetId = it.mediaAssetId, version = it.version) } }.onSuccess { seed = it }.onFailure { error = "记录加载失败。" } }
    FoodMindDetailScaffold("记录详情", onBack, actions = { IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "编辑") }; IconButton(onClick = { scope.launch { runCatching { if (type == "DRINK") client.deleteDrinkRecord(id) else client.deleteFoodRecord(id); seed?.mediaAssetId?.let { runCatching { client.deleteMediaAsset(it) } } }.onSuccess { onDeleted() }.onFailure { error = "删除失败。" } } }) { Icon(Icons.Outlined.DeleteOutline, "删除") } }) { padding ->
        val data = seed
        if (data == null) Column(Modifier.padding(padding).padding(24.dp)) { if (error == null) CircularProgressIndicator() else Text(error!!, color = FoodMindCoral) }
        else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text(data.name, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold); Text(listOf(data.place, data.occurredAt).filter(String::isNotBlank).joinToString(" · "), color = FoodMindMuted) }
            item { FoodMindSurfaceCard { Column { listOf("价格" to listOf(data.price, data.currency).filter(String::isNotBlank).joinToString(" "), "评分" to data.rating, "可见范围" to data.visibility, "再次选择" to when (data.repeat) { true -> "是"; false -> "否"; null -> "未记录" }).forEach { (label, value) -> Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Text(label, Modifier.weight(1f), color = FoodMindMuted); Text(value.ifBlank { "未记录" }, Modifier.weight(1f)) } } } } }
            if (data.comment.isNotBlank()) item { FoodMindSurfaceCard { Column { Text("评论", fontWeight = FontWeight.Bold); Text(data.comment, Modifier.padding(top = 7.dp)) } } }
            if (data.mediaAssetId != null) item { Text("此记录引用一个已上传的安全图片资源：${data.mediaAssetId}", color = FoodMindMuted, fontSize = 12.sp) }
            error?.let { item { Text(it, color = FoodMindCoral) } }
        }
    }
}
