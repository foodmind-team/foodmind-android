package com.foodmind.foodmind_android

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

class MainActivity : AppCompatActivity() {
    private var mode = Mode.RECOMMEND
    private var restaurantIndex = 0

    private val restaurantResults by lazy {
        listOf(
            Result(
                title = getString(R.string.result_restaurant_title),
                meta = getString(R.string.result_restaurant_meta),
                reason = getString(R.string.result_restaurant_reason),
                match = getString(R.string.result_match),
            ),
            Result(
                title = getString(R.string.result_restaurant_two_title),
                meta = getString(R.string.result_restaurant_two_meta),
                reason = getString(R.string.result_restaurant_two_reason),
                match = getString(R.string.result_restaurant_two_match),
            ),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindModeToggle()
        bindGenerator()
        bindNavigation()
        bindSecondaryActions()
        updateMode(Mode.RECOMMEND)
    }

    private fun bindModeToggle() {
        findViewById<MaterialButtonToggleGroup>(R.id.mode_toggle)
            .addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                updateMode(
                    if (checkedId == R.id.mode_cooking) Mode.COOKING else Mode.RECOMMEND,
                )
            }
    }

    private fun bindGenerator() {
        findViewById<MaterialButton>(R.id.generate_button).setOnClickListener {
            runGenerator()
        }
        findViewById<MaterialButton>(R.id.another_choice).setOnClickListener {
            if (mode == Mode.RECOMMEND) {
                restaurantIndex = (restaurantIndex + 1) % restaurantResults.size
            }
            showResult()
            toast(
                if (mode == Mode.RECOMMEND) {
                    R.string.fresh_group_option
                } else {
                    R.string.fresh_cooking_plan
                },
            )
        }
        findViewById<MaterialButton>(R.id.result_primary).setOnClickListener {
            toast(
                if (mode == Mode.RECOMMEND) {
                    R.string.shared_with_group
                } else {
                    R.string.cooking_plan_started
                },
            )
        }
    }

    private fun bindNavigation() {
        findViewById<View>(R.id.navigation_home).setOnClickListener {
            findViewById<NestedScrollView>(R.id.content_scroll).smoothScrollTo(0, 0)
        }
        findViewById<View>(R.id.navigation_groups).setOnClickListener {
            scrollTo(R.id.group_card)
        }
        findViewById<View>(R.id.navigation_explore).setOnClickListener {
            scrollTo(R.id.explore_section)
        }
        findViewById<View>(R.id.navigation_saved).setOnClickListener {
            toast(R.string.saved_preview)
        }
        findViewById<View>(R.id.navigation_profile).setOnClickListener {
            toast(R.string.profile_preview)
        }
    }

    private fun bindSecondaryActions() {
        findViewById<View>(R.id.edit_context).setOnClickListener {
            toast(
                if (mode == Mode.RECOMMEND) {
                    R.string.edit_group_context
                } else {
                    R.string.edit_pantry_context
                },
            )
        }
        findViewById<View>(R.id.group_card).setOnClickListener {
            toast(
                if (mode == Mode.RECOMMEND) {
                    R.string.group_preview
                } else {
                    R.string.pantry_preview
                },
            )
        }
        findViewById<View>(R.id.open_explore).setOnClickListener {
            toast(R.string.explore_preview)
        }
        findViewById<View>(R.id.explore_post_one).setOnClickListener {
            toast(R.string.post_one_preview)
        }
        findViewById<View>(R.id.explore_post_two).setOnClickListener {
            toast(R.string.post_two_preview)
        }
    }

    private fun runGenerator() {
        val button = findViewById<MaterialButton>(R.id.generate_button)
        button.isEnabled = false
        button.text = getString(
            if (mode == Mode.RECOMMEND) {
                R.string.generating_recommendation
            } else {
                R.string.generating_cooking_plan
            },
        )
        findViewById<View>(R.id.result_container).visibility = View.GONE

        button.postDelayed({
            button.isEnabled = true
            button.text = getString(
                if (mode == Mode.RECOMMEND) {
                    R.string.generate_again
                } else {
                    R.string.regenerate_plan
                },
            )
            showResult()
        }, GENERATION_DELAY_MS)
    }

    private fun showResult() {
        val result = if (mode == Mode.RECOMMEND) {
            restaurantResults[restaurantIndex]
        } else {
            Result(
                title = getString(R.string.result_cooking_title),
                meta = getString(R.string.result_cooking_meta),
                reason = getString(R.string.result_cooking_reason),
                match = getString(R.string.result_cooking_match),
            )
        }

        findViewById<TextView>(R.id.result_match).text = result.match
        findViewById<TextView>(R.id.result_title).text = result.title
        findViewById<TextView>(R.id.result_meta).text = result.meta
        findViewById<TextView>(R.id.result_reason).text = result.reason
        findViewById<MaterialButton>(R.id.result_primary).apply {
            text = getString(
                if (mode == Mode.RECOMMEND) {
                    R.string.share_with_group
                } else {
                    R.string.start_cooking
                },
            )
            setIconResource(
                if (mode == Mode.RECOMMEND) R.drawable.ic_groups else R.drawable.ic_chef,
            )
        }
        findViewById<View>(R.id.result_container).visibility = View.VISIBLE
    }

    private fun updateMode(nextMode: Mode) {
        mode = nextMode
        findViewById<View>(R.id.result_container).visibility = View.GONE

        val isRecommend = mode == Mode.RECOMMEND
        setText(
            R.id.page_eyebrow,
            if (isRecommend) R.string.recommend_eyebrow else R.string.cooking_eyebrow,
        )
        setText(
            R.id.page_title,
            if (isRecommend) R.string.recommend_page_title else R.string.cooking_page_title,
        )
        setText(
            R.id.page_support,
            if (isRecommend) R.string.recommend_page_support else R.string.cooking_page_support,
        )
        setText(
            R.id.context_label,
            if (isRecommend) R.string.recommending_for else R.string.planning_from,
        )
        setText(
            R.id.context_value,
            if (isRecommend) R.string.kitchen_table_context else R.string.pantry_context,
        )
        setText(
            R.id.signal_title,
            if (isRecommend) R.string.shared_ratings else R.string.pantry_expiry_signal,
        )
        setText(
            R.id.signal_support,
            if (isRecommend) R.string.enough_signal else R.string.pantry_expiry_support,
        )
        setText(
            R.id.context_one,
            if (isRecommend) R.string.context_time else R.string.cooking_context_time,
        )
        setText(
            R.id.context_two,
            if (isRecommend) R.string.context_range else R.string.cooking_context_serves,
        )
        setText(
            R.id.context_three,
            if (isRecommend) R.string.context_budget else R.string.cooking_context_spend,
        )
        setText(
            R.id.context_four,
            if (isRecommend) R.string.context_constraint else R.string.cooking_context_constraint,
        )
        setText(
            R.id.generator_label,
            if (isRecommend) R.string.foodmind_recommendation else R.string.foodmind_cooking_plan,
        )
        setText(
            R.id.generator_title,
            if (isRecommend) R.string.generator_recommend_title else R.string.generator_cooking_title,
        )
        setText(
            R.id.generator_support,
            if (isRecommend) R.string.generator_recommend_support else R.string.generator_cooking_support,
        )
        setText(
            R.id.generator_hint,
            if (isRecommend) R.string.generator_hint_recommend else R.string.generator_hint_cooking,
        )
        setText(
            R.id.group_eyebrow,
            if (isRecommend) R.string.core_group else R.string.pantry_snapshot,
        )
        setText(
            R.id.group_title,
            if (isRecommend) R.string.kitchen_table else R.string.twelve_items_ready,
        )
        setText(
            R.id.group_support,
            if (isRecommend) R.string.group_card_support else R.string.pantry_card_support,
        )
        setText(
            R.id.group_signal_title,
            if (isRecommend) R.string.strongest_signal else R.string.use_first,
        )
        setText(
            R.id.group_signal_value,
            if (isRecommend) R.string.strongest_signal_value else R.string.use_first_value,
        )

        findViewById<ImageView>(R.id.generator_icon).setImageResource(
            if (isRecommend) R.drawable.ic_groups else R.drawable.ic_chef,
        )
        findViewById<MaterialButton>(R.id.generate_button).apply {
            text = getString(
                if (isRecommend) {
                    R.string.generate_recommendation
                } else {
                    R.string.generate_cooking_plan
                },
            )
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (isRecommend) R.color.foodmind_coral else R.color.foodmind_lime,
                ),
            )
        }
    }

    private fun setText(viewId: Int, stringId: Int) {
        findViewById<TextView>(viewId).setText(stringId)
    }

    private fun scrollTo(viewId: Int) {
        val scroll = findViewById<NestedScrollView>(R.id.content_scroll)
        val target = findViewById<View>(viewId)
        scroll.post {
            scroll.smoothScrollTo(0, target.top)
        }
    }

    private fun toast(messageId: Int) {
        Toast.makeText(this, messageId, Toast.LENGTH_SHORT).show()
    }

    private data class Result(
        val title: String,
        val meta: String,
        val reason: String,
        val match: String,
    )

    private enum class Mode {
        RECOMMEND,
        COOKING,
    }

    private companion object {
        const val GENERATION_DELAY_MS = 850L
    }
}
