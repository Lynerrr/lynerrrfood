package com.lynerrr.food.ui

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.BackEventCompat
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lynerrr.food.FoodApp

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val updater = (application as FoodApp).updater
        setContent {
            FoodTheme {
                UpdateDialogs(updater)
                val nav = rememberNavController()
                val dayArg = navArgument("day") { type = NavType.LongType }
                NavHost(
                    nav,
                    startDestination = "diary",
                    enterTransition = { fadeIn(tween(TRANSITION_MS)) + slideInHorizontally(tween(TRANSITION_MS)) { it / 8 } },
                    exitTransition = { fadeOut(tween(TRANSITION_MS)) },
                    popEnterTransition = { fadeIn(tween(TRANSITION_MS)) },
                    popExitTransition = { fadeOut(tween(TRANSITION_MS)) + slideOutHorizontally(tween(TRANSITION_MS)) { it / 8 } },
                    // The back gesture (predictive back, on by default from Android 16) previews the exit while the
                    // finger moves. Navigation's default shrinks the screen without fading it, so on release it
                    // vanished at once; this one also fades and follows the swipe, so letting go just finishes it.
                    predictivePopEnterTransition = { fadeIn(tween(TRANSITION_MS)) },
                    predictivePopExitTransition = { edge ->
                        fadeOut(tween(TRANSITION_MS)) + scaleOut(tween(TRANSITION_MS), targetScale = 0.9f) +
                            slideOutHorizontally(tween(TRANSITION_MS)) { if (edge == BackEventCompat.EDGE_RIGHT) -it / 10 else it / 10 }
                    },
                ) {
                    composable("diary") {
                        DiaryScreen(
                            onAddFood = { day -> nav.navigate("search/$day") },
                            onShowNutrients = { day -> nav.navigate("nutrients/$day") },
                            onOpenProgress = { nav.navigate("progress") },
                            onOpenData = { nav.navigate("data") },
                        )
                    }
                    composable(
                        "search/{day}?q={q}",
                        listOf(dayArg, navArgument("q") { type = NavType.StringType; defaultValue = "" }),
                    ) { entry ->
                        val day = entry.arguments!!.getLong("day")
                        SearchScreen(
                            day = day,
                            initialQuery = entry.arguments!!.getString("q").orEmpty(),
                            onBack = { nav.popBackStack() },
                            onOpenFood = { id, grams -> nav.navigate("food/$id/$day" + (grams?.let { "?grams=$it" } ?: "")) },
                            onCreateFood = { name, barcode ->
                                nav.navigate("custom/0/$day?name=${Uri.encode(name)}&barcode=${Uri.encode(barcode.orEmpty())}")
                            },
                            onPhotoEstimate = { nav.navigate("photo/$day") },
                            onCreateRecipe = { nav.navigate("recipe/0/$day") },
                        )
                    }
                    composable("photo/{day}", listOf(dayArg)) { entry ->
                        PhotoEstimateScreen(
                            day = entry.arguments!!.getLong("day"),
                            onBack = { nav.popBackStack() },
                            onOpenSettings = { nav.navigate("data") },
                            onDone = { nav.popBackStack("diary", inclusive = false) },
                        )
                    }
                    composable(
                        "food/{id}/{day}?grams={grams}",
                        listOf(
                            navArgument("id") { type = NavType.IntType },
                            dayArg,
                            navArgument("grams") { type = NavType.StringType; defaultValue = "" },
                        ),
                    ) { entry ->
                        val id = entry.arguments!!.getInt("id")
                        val day = entry.arguments!!.getLong("day")
                        FoodScreen(
                            foodId = id,
                            day = day,
                            initialGrams = entry.arguments!!.getString("grams")?.toDoubleOrNull(),
                            onBack = { nav.popBackStack() },
                            onAdded = { nav.popBackStack("diary", inclusive = false) },
                            onEdit = { recipe -> nav.navigate(if (recipe) "recipe/$id/$day" else "custom/$id/$day") },
                            onCopy = { nav.navigate("custom/0/$day?copyOf=$id") },
                            onBestSources = { nutrient -> nav.navigate("sources/${Uri.encode(nutrient)}/$day") },
                        )
                    }
                    composable(
                        "custom/{id}/{day}?name={name}&barcode={barcode}&copyOf={copyOf}",
                        listOf(
                            navArgument("id") { type = NavType.IntType },
                            dayArg,
                            navArgument("name") { type = NavType.StringType; defaultValue = "" },
                            navArgument("barcode") { type = NavType.StringType; defaultValue = "" },
                            navArgument("copyOf") { type = NavType.StringType; defaultValue = "" },
                        ),
                    ) { entry ->
                        // id 0 = new food; custom foods have negative ids.
                        val id = entry.arguments!!.getInt("id").takeIf { it != 0 }
                        val day = entry.arguments!!.getLong("day")
                        CustomFoodScreen(
                            foodId = id,
                            initialName = entry.arguments!!.getString("name").orEmpty(),
                            barcode = entry.arguments!!.getString("barcode")?.ifBlank { null },
                            copyOf = entry.arguments!!.getString("copyOf")?.toIntOrNull(),
                            onBack = { nav.popBackStack() },
                            onSaved = { savedId ->
                                if (id != null) {
                                    nav.popBackStack()
                                } else {
                                    nav.navigate("food/$savedId/$day") {
                                        popUpTo("custom/{id}/{day}?name={name}&barcode={barcode}&copyOf={copyOf}") { inclusive = true }
                                    }
                                }
                            },
                        )
                    }
                    composable(
                        "recipe/{id}/{day}",
                        listOf(navArgument("id") { type = NavType.IntType }, dayArg),
                    ) { entry ->
                        // id 0 = new recipe; recipes are custom foods with negative ids.
                        val id = entry.arguments!!.getInt("id").takeIf { it != 0 }
                        val day = entry.arguments!!.getLong("day")
                        RecipeScreen(
                            recipeId = id,
                            onBack = { nav.popBackStack() },
                            onSaved = { savedId ->
                                if (id != null) {
                                    nav.popBackStack()
                                } else {
                                    // Straight to the new recipe's page, where it can be added to the diary.
                                    nav.navigate("food/$savedId/$day") { popUpTo("recipe/{id}/{day}") { inclusive = true } }
                                }
                            },
                        )
                    }
                    composable("nutrients/{day}", listOf(dayArg)) { entry ->
                        val day = entry.arguments!!.getLong("day")
                        NutrientsScreen(
                            day = day,
                            onBack = { nav.popBackStack() },
                            onBestSources = { nutrient -> nav.navigate("sources/${Uri.encode(nutrient)}/$day") },
                        )
                    }
                    composable(
                        "sources/{nutrient}/{day}",
                        listOf(navArgument("nutrient") { type = NavType.StringType }, dayArg),
                    ) { entry ->
                        val day = entry.arguments!!.getLong("day")
                        BestSourcesScreen(
                            nutrient = entry.arguments!!.getString("nutrient")!!,
                            onBack = { nav.popBackStack() },
                            onOpenFood = { id -> nav.navigate("food/$id/$day") },
                            onSearch = { query -> nav.navigate("search/$day?q=${Uri.encode(query)}") },
                        )
                    }
                    composable("progress") {
                        ProgressScreen(onBack = { nav.popBackStack() }, onEditPlan = { nav.navigate("plan") })
                    }
                    composable("plan") {
                        PlanSetupScreen(onBack = { nav.popBackStack() })
                    }
                    composable("data") {
                        DataScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Checks for updates (when due) every time the app comes back, not just on a cold start.
        (application as FoodApp).updater.onResume()
    }

    override fun onPause() {
        super.onPause()
        (application as FoodApp).updater.onPause()
    }
}

/** Length of screen-change animations. */
private const val TRANSITION_MS = 250
