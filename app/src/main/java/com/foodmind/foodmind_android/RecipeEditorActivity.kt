package com.foodmind.foodmind_android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.foodmind.foodmind_android.domain.repository.RecipeDraftStore
import com.foodmind.foodmind_android.domain.repository.RecipeDraft
import com.foodmind.foodmind_android.domain.repository.UserRecipeRepository
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.FoodMindNetwork
import com.foodmind.foodmind_android.core.network.FoodMindSession
import kotlinx.coroutines.launch

class RecipeEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FoodMindSession.initialize(this)
        val apiClient = FoodMindApiClient(
            FoodMindNetwork.createApi(BuildConfig.FOODMIND_API_BASE_URL, FoodMindSession.tokenStore),
            FoodMindSession.tokenStore,
        )
        val id = intent.getStringExtra(EXTRA_RECIPE_ID)
        val existing = id?.let(RecipeDraftStore::find)
        setContent {
            FoodMindTheme {
                RecipeEditorScreen(
                    initialName = existing?.name.orEmpty(),
                    initialServings = existing?.servings?.toString() ?: "2",
                    initialMinutes = existing?.minutes?.toString() ?: "30",
                    isEditing = existing != null,
                    onBack = ::finish,
                    onSave = { name, servings, minutes ->
                        val localDraft = RecipeDraftStore.save(id, name.trim(), servings, minutes)
                        lifecycleScope.launch {
                            runCatching {
                                val repository = UserRecipeRepository(apiClient)
                                if (id == null) repository.create(localDraft) else repository.update(localDraft)
                            }
                            // 无登录会话/后端时，localDraft 已保留为明确的演示 fallback。
                            finish()
                        }
                    },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_RECIPE_ID = "recipe_id"
        fun intent(context: Context, id: String): Intent =
            Intent(context, RecipeEditorActivity::class.java).putExtra(EXTRA_RECIPE_ID, id)
    }
}

@Composable
private fun RecipeEditorScreen(
    initialName: String,
    initialServings: String,
    initialMinutes: String,
    isEditing: Boolean,
    onBack: () -> Unit,
    onSave: (String, Int, Int) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var servings by remember(initialServings) { mutableStateOf(initialServings) }
    var minutes by remember(initialMinutes) { mutableStateOf(initialMinutes) }
    val servingsValue = servings.toIntOrNull()
    val minutesValue = minutes.toIntOrNull()
    val valid = name.trim().isNotEmpty() && servingsValue != null && servingsValue in 1..24 && minutesValue != null && minutesValue in 1..1440

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(if (isEditing) "编辑菜谱" else "新增菜谱", color = FoodMindGreenDark)
        Text("优先保存到 C-08 服务端；服务不可用时保留本地演示草稿。", color = FoodMindMuted)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("菜谱名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = servings,
            onValueChange = { servings = it.filter(Char::isDigit) },
            label = { Text("份数（1–24）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = minutes,
            onValueChange = { minutes = it.filter(Char::isDigit) },
            label = { Text("预计分钟（1–1440）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSave(name, servingsValue ?: 2, minutesValue ?: 30) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存菜谱") }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }
    }
}
