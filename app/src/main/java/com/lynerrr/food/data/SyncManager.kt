package com.lynerrr.food.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SyncTask { IMPORT, USDA_IMPORT, PRODUCTS_IMPORT, PRODUCTS_UPDATE }

sealed interface SyncResult {
    data class Imported(val foods: Int) : SyncResult
    data class ProductsImported(val products: Int, val version: String) : SyncResult
    data object ProductsUpToDate : SyncResult
    data object Cancelled : SyncResult
    data class Failed(val message: String) : SyncResult
}

data class SyncState(
    val running: Boolean = false,
    val task: SyncTask? = null,
    val done: Int = 0,
    val total: Int = 0,
    val result: SyncResult? = null,
)

/** Runs long imports and downloads in the app scope so they keep going when the user leaves the screen. */
class SyncManager(
    private val bundled: BundledDatabase,
    private val usda: BundledDatabase,
    private val products: ProductsDatabase,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()
    private var job: Job? = null

    /** Loads the databases shipped with the app the first time a new version of them is seen. */
    fun importBundledIfNeeded() = scope.launch {
        if (bundled.needsImport()) start(SyncTask.IMPORT) { bundled.import(::progress) }?.join()
        if (usda.needsImport()) start(SyncTask.USDA_IMPORT) { usda.import(::progress) }?.join()
        if (products.bundledNeedsImport()) start(SyncTask.PRODUCTS_IMPORT) { products.importBundled(::progress) }?.join()
    }

    /** Fetches a newer packaged-products database from [manifestUrl], if there is one. */
    fun updateProducts(manifestUrl: String) = start(SyncTask.PRODUCTS_UPDATE) { products.update(manifestUrl, ::progress) }

    fun cancel() {
        job?.cancel()
    }

    private fun progress(done: Int, total: Int) = _state.update { it.copy(done = done, total = total) }

    private fun start(task: SyncTask, block: suspend () -> SyncResult): Job? {
        if (job?.isActive == true) return null
        return scope.launch {
            _state.value = SyncState(running = true, task = task)
            var result: SyncResult = SyncResult.Cancelled
            try {
                result = block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                result = SyncResult.Failed(e.message ?: e.javaClass.simpleName)
            } finally {
                _state.update { it.copy(running = false, result = result) }
            }
        }.also { job = it }
    }
}
