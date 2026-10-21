package com.lynerrr.food.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException

class HealthDietClient {
    suspend fun categoryPage(categoryId: Int): Document = get("/base_of_food/food_$categoryId/")

    suspend fun foodPage(foodId: Int): Document = get("/base_of_food/sostav/$foodId.php")

    private suspend fun get(path: String): Document = withContext(Dispatchers.IO) {
        var attempt = 1
        while (true) {
            try {
                return@withContext Jsoup.connect(BASE_URL + path)
                    .userAgent(USER_AGENT)
                    .timeout(20_000)
                    .get()
            } catch (e: IOException) {
                // Client errors (404, 429 rate limit) won't improve by retrying right away.
                if (e is HttpStatusException && e.statusCode in 400..499) throw e
                if (attempt >= MAX_ATTEMPTS) throw e
                delay(1_000L * attempt++)
            }
        }
        @Suppress("UNREACHABLE_CODE")
        throw IllegalStateException()
    }

    companion object {
        const val BASE_URL = "https://health-diet.ru"
        const val DEFAULT_CATEGORY = 24501
        private const val MAX_ATTEMPTS = 3
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
    }
}
