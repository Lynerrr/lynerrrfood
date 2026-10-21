@file:OptIn(ExperimentalMaterial3Api::class)

package com.lynerrr.food.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lynerrr.food.BuildConfig
import com.lynerrr.food.R
import com.lynerrr.food.data.AiSettings
import com.lynerrr.food.data.FoodRepository
import com.lynerrr.food.data.ProductsDatabase
import com.lynerrr.food.data.SyncManager
import com.lynerrr.food.data.SyncTask
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DataViewModel(
    repo: FoodRepository,
    val sync: SyncManager,
    private val products: ProductsDatabase,
    val ai: AiSettings,
) : ViewModel() {
    val productCount = repo.productCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Re-read after each sync so a finished update shows its new version. */
    val productsVersion = sync.state.map { products.version }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), products.version)

    val foodCount = repo.foodCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val detailedCount = repo.detailedCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

/** Language tags offered in the picker; "" follows the system language. */
private val LANGUAGES = listOf("" to R.string.language_system, "en" to R.string.language_en, "ru" to R.string.language_ru)

@Composable
fun DataScreen(onBack: () -> Unit) {
    val vm = appViewModel { DataViewModel(it.repository, it.sync, it.products, it.ai) }
    val savedKey by vm.ai.apiKey.collectAsStateWithLifecycle()
    var keyInput by remember(savedKey) { mutableStateOf(savedKey) }
    val productCount by vm.productCount.collectAsStateWithLifecycle()
    val productsVersion by vm.productsVersion.collectAsStateWithLifecycle()
    val foodCount by vm.foodCount.collectAsStateWithLifecycle()
    val detailedCount by vm.detailedCount.collectAsStateWithLifecycle()
    val state by vm.sync.state.collectAsStateWithLifecycle()
    // Each card shows progress and results of its own tasks only.
    val productsTask = state.task == SyncTask.PRODUCTS_IMPORT || state.task == SyncTask.PRODUCTS_UPDATE
    val currentLanguage = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🌐 " + stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LANGUAGES.forEach { (tag, label) ->
                            FilterChip(
                                selected = currentLanguage == tag,
                                onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) },
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.language_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("✨ " + stringResource(R.string.ai_settings_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.ai_settings_explainer), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text(stringResource(R.string.api_key)) },
                        placeholder = { Text("sk-ant-…") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { vm.ai.setApiKey(keyInput) }, enabled = keyInput.trim() != savedKey) {
                            Text(stringResource(R.string.save))
                        }
                        if (savedKey.isNotEmpty()) {
                            Text(stringResource(R.string.api_key_saved), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📦 " + stringResource(R.string.packaged_products), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.products_count, productCount), style = MaterialTheme.typography.bodyLarge)
                    productsVersion?.let { v ->
                        Text(
                            stringResource(R.string.products_version, "${v.take(4)}-${v.drop(4).take(2)}-${v.drop(6).take(2)}"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        stringResource(R.string.products_attribution),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (productsTask) {
                        if (state.running) {
                            Text(syncTaskLabel(state.task), style = MaterialTheme.typography.titleSmall)
                            LinearProgressIndicator(
                                progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            state.result?.let { Text(syncResultText(it)) }
                        }
                    }
                    // Only offered once a hosted manifest URL is configured (gradle property productsManifestUrl).
                    if (BuildConfig.PRODUCTS_MANIFEST_URL.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { vm.sync.updateProducts(BuildConfig.PRODUCTS_MANIFEST_URL) },
                            enabled = !state.running,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.check_updates)) }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🗄️ " + stringResource(R.string.food_database), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.foods_in_catalog, foodCount), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.foods_with_details, detailedCount), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.database_explainer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (state.running && !productsTask) {
                        Text(syncTaskLabel(state.task), style = MaterialTheme.typography.titleSmall)
                        LinearProgressIndicator(
                            progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(stringResource(R.string.progress_count, state.done, state.total))
                    } else if (!productsTask) {
                        state.result?.let { Text(syncResultText(it)) }
                    }
                }
            }
        }
    }
}
