package com.lynerrr.food.data

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Base64

/** One food Claude sees in a photo, with its estimate for the whole visible portion. */
data class EstimatedFood(
    val nameEn: String,
    val nameRu: String,
    /** Short generic Russian name, as in Russian nutrition tables, for matching the food database. */
    val searchQueryRu: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    /** "low", "medium" or "high". */
    val confidence: String,
)

data class PhotoEstimate(val items: List<EstimatedFood>, val note: String)

class PhotoAnalysisException(message: String) : Exception(message)

/** Estimates the foods and portions in a meal photo with Claude (Anthropic API, the user's own key). */
class FoodPhotoAnalyzer(private val apiKey: String) {

    suspend fun analyze(jpeg: ByteArray, userNote: String, russian: Boolean): PhotoEstimate = withContext(Dispatchers.IO) {
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
        try {
            val image = ImageBlockParam.builder()
                .source(
                    Base64ImageSource.builder()
                        .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                        .data(Base64.getEncoder().encodeToString(jpeg))
                        .build()
                )
                .build()
            val request = buildString {
                append("Estimate what's in this photo for my food diary.")
                if (userNote.isNotBlank()) append(" My note: ").append(userNote.trim())
                append(if (russian) " Write the note in Russian." else " Write the note in English.")
            }
            val params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(16000L)
                .system(SYSTEM_PROMPT)
                .outputConfig(OutputConfig.builder().format(JsonOutputFormat.builder().schema(SCHEMA).build()).build())
                .addUserMessageOfBlockParams(
                    listOf(
                        ContentBlockParam.ofImage(image),
                        ContentBlockParam.ofText(TextBlockParam.builder().text(request).build()),
                    )
                )
                // If a safety classifier declines, the server retries on a fallback model instead of failing.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build()

            val response = client.messages().create(params)
            if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
                throw PhotoAnalysisException("Claude declined to analyze this photo")
            }
            // Plain Kotlin on purpose: Optional.stream()/Stream.toList() need Android 13+/14+.
            val text = response.content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString("")
            parse(text)
        } finally {
            client.close()
        }
    }

    private fun parse(text: String): PhotoEstimate {
        val json = try {
            JSONObject(text)
        } catch (e: org.json.JSONException) {
            throw PhotoAnalysisException("Unexpected answer from Claude")
        }
        val items = json.getJSONArray("items")
        return PhotoEstimate(
            items = (0 until items.length()).map { i ->
                val o = items.getJSONObject(i)
                EstimatedFood(
                    nameEn = o.getString("name_en"),
                    nameRu = o.getString("name_ru"),
                    searchQueryRu = o.getString("search_query_ru"),
                    grams = o.getDouble("grams"),
                    kcal = o.getDouble("kcal"),
                    protein = o.getDouble("protein_g"),
                    fat = o.getDouble("fat_g"),
                    carbs = o.getDouble("carbs_g"),
                    confidence = o.getString("confidence"),
                )
            }.filter { it.grams > 0 },
            note = json.optString("note"),
        )
    }

    companion object {
        const val MODEL = "claude-opus-5-5"

        private val SYSTEM_PROMPT = """
            You estimate meals from photos for a calorie and nutrient diary. List each food someone would log
            separately (a sandwich is one item; rice and the chicken next to it are two), with the edible weight
            actually on the plate and the calories and macros for that weight. Judge portion size from plates,
            cutlery, hands and packaging; read labels when a package is visible. Assume typical home or
            restaurant preparation unless the photo or the note says otherwise, and count visible oil, sauces
            and dressings. search_query_ru is a short, plain Russian name the way Russian nutrition tables
            write it (for example "гречка отварная", "куриная грудка жареная"). If there's no food in the
            photo, return no items and say so in the note. Use the note for anything the user should know,
            such as assumptions that change the numbers a lot.
        """.trimIndent()

        private val SCHEMA: JsonOutputFormat.Schema = JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .putAdditionalProperty("required", JsonValue.from(listOf("items", "note")))
            .putAdditionalProperty(
                "properties",
                JsonValue.from(
                    mapOf(
                        "note" to mapOf("type" to "string"),
                        "items" to mapOf(
                            "type" to "array",
                            "items" to mapOf(
                                "type" to "object",
                                "additionalProperties" to false,
                                "required" to listOf(
                                    "name_en", "name_ru", "search_query_ru", "grams", "kcal",
                                    "protein_g", "fat_g", "carbs_g", "confidence",
                                ),
                                "properties" to mapOf(
                                    "name_en" to mapOf("type" to "string"),
                                    "name_ru" to mapOf("type" to "string"),
                                    "search_query_ru" to mapOf("type" to "string"),
                                    "grams" to mapOf("type" to "number"),
                                    "kcal" to mapOf("type" to "number"),
                                    "protein_g" to mapOf("type" to "number"),
                                    "fat_g" to mapOf("type" to "number"),
                                    "carbs_g" to mapOf("type" to "number"),
                                    "confidence" to mapOf("type" to "string", "enum" to listOf("low", "medium", "high")),
                                ),
                            ),
                        ),
                    )
                ),
            )
            .build()
    }
}
