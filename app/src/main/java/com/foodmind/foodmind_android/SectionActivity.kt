package com.foodmind.foodmind_android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodmind.foodmind_android.core.network.FoodMindApiClient
import com.foodmind.foodmind_android.core.network.FoodMindNetwork
import com.foodmind.foodmind_android.core.network.FoodMindSession
import kotlinx.coroutines.launch

/** Shared Compose shell for the first non-home destinations. */
class SectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val section = Section.from(intent.getStringExtra(EXTRA_SECTION))
        if (section == Section.EXPLORE) {
            startActivity(Intent(this, ExploreActivity::class.java))
            finish()
            return
        }
        if (section == Section.GROUPS) {
            startActivity(Intent(this, GroupsActivity::class.java))
            finish()
            return
        }
        setContent {
            FoodMindTheme {
                SectionScreen(
                    section = section,
                    onBack = ::finish,
                    onLogin = { startActivity(Intent(this, LoginActivity::class.java)) },
                    onDashboard = { startActivity(Intent(this, DashboardActivity::class.java)) },
                    onChat = { startActivity(Intent(this, ChatActivity::class.java)) },
                    onLogout = {
                        FoodMindSession.initialize(this)
                        val api = FoodMindApiClient(
                            FoodMindNetwork.createApi(BuildConfig.FOODMIND_API_BASE_URL, FoodMindSession.tokenStore),
                            FoodMindSession.tokenStore,
                        )
                        lifecycleScope.launch {
                            runCatching { api.logout() }
                            FoodMindSession.tokenStore.clear()
                        }
                    },
                )
            }
        }
    }

    enum class Section(
        val title: Int,
        val support: Int,
        val cardOne: Int,
        val cardTwo: Int,
        val cardThree: Int,
        val note: Int,
    ) {
        GROUPS(
            R.string.section_groups_title,
            R.string.section_groups_support,
            R.string.section_groups_card_one,
            R.string.section_groups_card_two,
            R.string.section_groups_card_three,
            R.string.section_groups_note,
        ),
        EXPLORE(
            R.string.section_explore_title,
            R.string.section_explore_support,
            R.string.section_explore_card_one,
            R.string.section_explore_card_two,
            R.string.section_explore_card_three,
            R.string.section_explore_note,
        ),
        PROFILE(
            R.string.section_profile_title,
            R.string.section_profile_support,
            R.string.section_profile_card_one,
            R.string.section_profile_card_two,
            R.string.section_profile_card_three,
            R.string.section_profile_note,
        );

        companion object {
            fun from(value: String?): Section = entries.firstOrNull { it.name == value } ?: PROFILE
        }
    }

    companion object {
        private const val EXTRA_SECTION = "section"

        fun intent(context: Context, section: Section): Intent =
            Intent(context, SectionActivity::class.java).putExtra(EXTRA_SECTION, section.name)
    }
}

@Composable
private fun SectionScreen(
    section: SectionActivity.Section,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onDashboard: () -> Unit,
    onChat: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack) { Text("返回") }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(section.title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = FoodMindInk,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(section.support), color = FoodMindMuted)
            }
            listOf(section.cardOne, section.cardTwo, section.cardThree).forEach { card ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, FoodMindLine),
                    ) {
                        Text(
                            text = stringResource(card),
                            modifier = Modifier.padding(18.dp),
                            color = FoodMindInk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
            item {
                Text(
                    text = stringResource(section.note),
                    modifier = Modifier.padding(top = 8.dp),
                    color = FoodMindMuted,
                    fontSize = 12.sp,
                )
            }
            if (section == SectionActivity.Section.PROFILE) {
                item {
                    OutlinedButton(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                        Text("登录 / 切换账号")
                    }
                }
                item {
                    OutlinedButton(onClick = onDashboard, modifier = Modifier.fillMaxWidth()) {
                        Text("打开数据看板")
                    }
                }
                item {
                    OutlinedButton(onClick = onChat, modifier = Modifier.fillMaxWidth()) {
                        Text("打开 FoodMind 助手")
                    }
                }
                item {
                    OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                        Text("退出登录")
                    }
                }
            }
        }
    }
}
