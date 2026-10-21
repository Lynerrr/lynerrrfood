@file:OptIn(ExperimentalMaterial3Api::class)

package com.lynerrr.food.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anthropic.errors.UnauthorizedException
import com.lynerrr.food.FoodApp
import com.lynerrr.food.R
import com.lynerrr.food.data.EstimatedFood
import com.lynerrr.food.data.FoodEntity
import com.lynerrr.food.data.FoodIds
import com.lynerrr.food.data.FoodSource
import com.lynerrr.food.data.FoodPhotoAnalyzer
import com.lynerrr.food.data.FoodRepository
import com.lynerrr.food.data.FoodSearch
import com.lynerrr.food.data.MealPhoto
import com.lynerrr.food.data.NutrientCatalog
import com.lynerrr.food.data.NutrientGroup
import com.lynerrr.food.data.PhotoAnalysisException
import com.lynerrr.food.data.formatAmount
import com.lynerrr.food.net.HealthDietParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalTime

/** One food from the estimate, as the user is adjusting it. */
data class EstimateItem(
    val estimate: EstimatedFood,
    val grams: String,
    /** The closest food in the database, if any; its full nutrient data is used when [useDatabase]. */
    val match: FoodEntity?,
    val useDatabase: Boolean,
    val include: Boolean,
) {
    val gramsValue get() = grams.toDoubleOrNull() ?: 0.0

    /** Calories for the chosen grams, from the database food or from Claude's estimate. */
    val kcal: Double
        get() = if (useDatabase && match?.kcal != null) match.kcal * gramsValue / 100
        else estimate.kcal * gramsValue / estimate.grams
}

sealed interface EstimateStatus {
    data object Idle : EstimateStatus
    data object Analyzing : EstimateStatus
    data class Failed(val message: String?) : EstimateStatus
}

class PhotoEstimateViewModel(private val app: FoodApp, private val day: Long) : ViewModel() {
    private val repo: FoodRepository = app.repository
    val apiKey = app.ai.apiKey
    val photo = MutableStateFlow<Bitmap?>(null)
    val note = MutableStateFlow("")
    val status = MutableStateFlow<EstimateStatus>(EstimateStatus.Idle)
    val items = MutableStateFlow<List<EstimateItem>>(emptyList())
    val estimateNote = MutableStateFlow("")
    val meal = MutableStateFlow(LocalTime.now().hour.let { h -> if (h < 11) 0 else if (h < 16) 1 else if (h < 21) 2 else 3 })

    fun setPhoto(uri: Uri) = viewModelScope.launch {
        items.value = emptyList()
        estimateNote.value = ""
        status.value = try {
            photo.value = withContext(Dispatchers.IO) { MealPhoto.load(app, uri) }
            EstimateStatus.Idle
        } catch (e: Exception) {
            EstimateStatus.Failed(e.message)
        }
    }

    fun analyze(russian: Boolean) = viewModelScope.launch {
        val bitmap = photo.value ?: return@launch
        status.value = EstimateStatus.Analyzing
        status.value = try {
            val jpeg = withContext(Dispatchers.Default) { MealPhoto.toJpeg(bitmap) }
            val result = FoodPhotoAnalyzer(apiKey.value).analyze(jpeg, note.value, russian)
            // Match against health-diet.ru foods only (unless turned off): full vitamin and mineral data, and Russian
            // names like the estimate's search query.
            val useSite = FoodSource.HEALTH_DIET in app.sources.enabled.value
            val search = withContext(Dispatchers.Default) {
                FoodSearch(repo.allFoods().first().filter {
                    useSite && FoodIds.source(it.id) == FoodSource.HEALTH_DIET && it.detailsFetchedAt != null
                })
            }
            items.value = result.items.map { e ->
                val match = search.search(e.searchQueryRu, null, limit = 1).firstOrNull()
                EstimateItem(e, formatGrams(e.grams), match, useDatabase = match != null, include = true)
            }
            estimateNote.value = result.note
            EstimateStatus.Idle
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnauthorizedException) {
            EstimateStatus.Failed(app.getString(R.string.ai_key_rejected))
        } catch (e: PhotoAnalysisException) {
            EstimateStatus.Failed(e.message)
        } catch (e: Exception) {
            EstimateStatus.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    fun update(index: Int, change: (EstimateItem) -> EstimateItem) =
        items.update { list -> list.mapIndexed { i, item -> if (i == index) change(item) else item } }

    /** Logs the included items. Foods without a database match are saved as AI-estimate foods first. */
    fun addToDiary(russian: Boolean, onDone: () -> Unit) = viewModelScope.launch {
        val energy = NutrientCatalog.editable(NutrientGroup.ENERGY).associateBy { it.siteName }
        items.value.filter { it.include && it.gramsValue > 0 }.forEach { item ->
            val foodId = if (item.useDatabase && item.match != null) item.match.id else {
                val e = item.estimate
                val factor = 100 / e.grams
                val per100g = mapOf(
                    HealthDietParser.KCAL to e.kcal, HealthDietParser.PROTEIN to e.protein,
                    HealthDietParser.FAT to e.fat, HealthDietParser.CARBS to e.carbs,
                ).mapNotNull { (name, amount) -> energy[name]?.let { it to amount * factor } }.toMap()
                repo.saveCustomFood(null, if (russian) e.nameRu else e.nameEn, "✨", per100g, category = FoodRepository.AI_CATEGORY)
            }
            repo.addToDiary(day, meal.value, foodId, item.gramsValue)
        }
        onDone()
    }

    private fun formatGrams(g: Double) = if (g % 1.0 == 0.0) g.toInt().toString() else "%.0f".format(java.util.Locale.ROOT, g)
}

@Composable
fun PhotoEstimateScreen(day: Long, onBack: () -> Unit, onOpenSettings: () -> Unit, onDone: () -> Unit) {
    val vm = appViewModel { PhotoEstimateViewModel(it, day) }
    val apiKey by vm.apiKey.collectAsStateWithLifecycle()
    val photo by vm.photo.collectAsStateWithLifecycle()
    val note by vm.note.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val estimateNote by vm.estimateNote.collectAsStateWithLifecycle()
    val meal by vm.meal.collectAsStateWithLifecycle()
    val russian = isRussian()
    val context = LocalContext.current

    // The camera app writes into our cache through a FileProvider; no camera permission needed.
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) pendingUri?.let { vm.setPhoto(Uri.parse(it)) }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::setPhoto)
    }
    fun capture() {
        val file = File(context.cacheDir, "photos/meal.jpg").apply { parentFile?.mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
        pendingUri = uri.toString()
        takePicture.launch(uri)
    }
    fun pick() = pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.photo_estimate_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (apiKey.isBlank()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.ai_needs_key), style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    photo?.let {
                        Image(
                            it.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.large),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = ::capture, modifier = Modifier.weight(1f)) {
                            Text("📷 " + stringResource(if (photo == null) R.string.take_photo else R.string.retake_photo))
                        }
                        OutlinedButton(onClick = ::pick, modifier = Modifier.weight(1f)) {
                            Text("🖼️ " + stringResource(R.string.choose_photo))
                        }
                    }
                }
            }

            if (photo != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { vm.note.value = it },
                            label = { Text(stringResource(R.string.photo_note)) },
                            placeholder = { Text(stringResource(R.string.photo_note_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { vm.analyze(russian) },
                            enabled = apiKey.isNotBlank() && status != EstimateStatus.Analyzing,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("✨ " + stringResource(R.string.analyze_photo)) }
                        Text(
                            stringResource(R.string.ai_cost_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            when (val s = status) {
                EstimateStatus.Analyzing -> item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.analyzing))
                    }
                }
                is EstimateStatus.Failed -> item {
                    Text(s.message ?: stringResource(R.string.ai_failed), color = MaterialTheme.colorScheme.error)
                }
                EstimateStatus.Idle -> Unit
            }

            if (estimateNote.isNotBlank()) {
                item {
                    Text("✨ $estimateNote", style = MaterialTheme.typography.bodyMedium)
                }
            }

            itemsIndexed(items) { index, item ->
                EstimateCard(
                    item = item,
                    russian = russian,
                    onChange = { change -> vm.update(index, change) },
                )
            }

            if (items.isNotEmpty()) {
                item {
                    val included = items.filter { it.include && it.gramsValue > 0 }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.estimate_total, included.sumOf { it.kcal }.toInt()),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        MealPicker(meal) { vm.meal.value = it }
                        Button(
                            onClick = { vm.addToDiary(russian, onDone) },
                            enabled = included.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.add_items_to_day, included.size, dayLabel(day).lowercase())) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EstimateCard(item: EstimateItem, russian: Boolean, onChange: ((EstimateItem) -> EstimateItem) -> Unit) {
    val e = item.estimate
    val factor = item.gramsValue / e.grams
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.include, onCheckedChange = { checked -> onChange { it.copy(include = checked) } })
                Column(Modifier.weight(1f)) {
                    Text(if (russian) e.nameRu else e.nameEn, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            when (e.confidence) {
                                "high" -> R.string.confidence_high
                                "low" -> R.string.confidence_low
                                else -> R.string.confidence_medium
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("${item.kcal.toInt()} ${unitLabel("ккал")}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                GramsField(item.grams, { g -> onChange { it.copy(grams = g) } }, Modifier.width(140.dp))
                Spacer(Modifier.width(12.dp))
                if (!item.useDatabase || item.match == null) {
                    Text(
                        stringResource(
                            R.string.entry_macros_short,
                            formatAmount(e.protein * factor), formatAmount(e.fat * factor), formatAmount(e.carbs * factor),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (item.match != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.use_database_values), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            item.match.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = item.useDatabase, onCheckedChange = { use -> onChange { it.copy(useDatabase = use) } })
                }
            } else {
                Text(
                    stringResource(R.string.no_database_match),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
