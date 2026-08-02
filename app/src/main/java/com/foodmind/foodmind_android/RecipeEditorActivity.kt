package com.foodmind.foodmind_android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.FoodMindSession
import com.foodmind.foodmind_android.domain.repository.RecipeDraft
import com.foodmind.foodmind_android.domain.repository.RecipeDraftStore

class RecipeEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FoodMindSession.initialize(this); RecipeDraftStore.initialize(this, FoodMindSession.tokenStore.userId())
        val id = intent.getStringExtra(EXTRA_RECIPE_ID); val existing = id?.let(RecipeDraftStore::find)
        setContent { FoodMindTheme { RecipeEditorScreen(existing, ::finish) { draft -> RecipeDraftStore.save(id, draft.name, draft.servings, draft.minutes, draft.category, draft.ingredients, draft.steps, draft.tags, draft.allergenHints, draft.imageUrl); finish() } } }
    }
    companion object { private const val EXTRA_RECIPE_ID = "recipe_id"; fun intent(context: Context, id: String) = Intent(context, RecipeEditorActivity::class.java).putExtra(EXTRA_RECIPE_ID, id) }
}

@Composable
private fun RecipeEditorScreen(existing: RecipeDraft?, onBack: () -> Unit, onSave: (RecipeDraft) -> Unit) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }; var servings by remember { mutableStateOf(existing?.servings?.toString() ?: "2") }; var minutes by remember { mutableStateOf(existing?.minutes?.toString() ?: "30") }
    var category by remember { mutableStateOf(existing?.category ?: "家常") }; var ingredients by remember { mutableStateOf(existing?.ingredients?.joinToString("\n").orEmpty()) }; var steps by remember { mutableStateOf(existing?.steps?.joinToString("\n").orEmpty()) }
    var tags by remember { mutableStateOf(existing?.tags?.joinToString("，").orEmpty()) }; var allergens by remember { mutableStateOf(existing?.allergenHints?.joinToString("，").orEmpty()) }
    val servingValue = servings.toIntOrNull(); val minuteValue = minutes.toIntOrNull(); val ingredientLines = ingredients.lines().map(String::trim).filter(String::isNotBlank); val stepLines = steps.lines().map(String::trim).filter(String::isNotBlank)
    val valid = name.isNotBlank() && servingValue != null && servingValue in 1..24 && minuteValue != null && minuteValue in 1..1440 && ingredientLines.isNotEmpty() && stepLines.isNotEmpty()
    FoodMindDetailScaffold(if (existing == null) "新增菜谱" else "编辑菜谱", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            item { Text("命名并写清做法", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold); Text("菜谱仅保存在本机并按账号隔离。每行一种食材或一个步骤。", color = FoodMindMuted) }
            item { OutlinedTextField(name, { name = it }, label = { Text("菜谱名称") }, modifier = Modifier.fillMaxWidth()) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(servings, { servings = it.filter(Char::isDigit) }, label = { Text("份数") }, modifier = Modifier.weight(1f)); OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("分钟") }, modifier = Modifier.weight(1f)) } }
            item { OutlinedTextField(category, { category = it }, label = { Text("分类") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(ingredients, { ingredients = it }, label = { Text("食材（每行一种，可带数量和单位）") }, minLines = 6, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(steps, { steps = it }, label = { Text("步骤（每行一步）") }, minLines = 6, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(tags, { tags = it }, label = { Text("标签（逗号分隔）") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(allergens, { allergens = it }, label = { Text("过敏原提示（逗号分隔）") }, modifier = Modifier.fillMaxWidth()) }
            item { Button(onClick = { onSave(RecipeDraft(existing?.id.orEmpty(), name.trim(), servingValue ?: 2, minuteValue ?: 30, category.trim().ifBlank { "家常" }, ingredientLines, stepLines, tags.split(',', '，').map(String::trim).filter(String::isNotBlank), allergens.split(',', '，').map(String::trim).filter(String::isNotBlank), existing?.imageUrl)) }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text("保存菜谱") } }
        }
    }
}
