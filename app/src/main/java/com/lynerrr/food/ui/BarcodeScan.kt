package com.lynerrr.food.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.lynerrr.food.R
import com.lynerrr.food.data.ProductsDatabase
import com.lynerrr.food.net.OpenFoodFacts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ScanState {
    data object Idle : ScanState
    data class Looking(val code: String) : ScanState
    /** Neither the app nor Open Food Facts has nutrition for this code; [name]/[brand] if OFF knows the product. */
    data class NotFound(val code: String, val name: String?, val brand: String?) : ScanState
    data class Failed(val message: String) : ScanState
}

/** Barcode → food: the app's own database first (products and foods created after earlier scans), then Open Food Facts. */
class BarcodeViewModel(private val products: ProductsDatabase) : ViewModel() {
    val state = MutableStateFlow<ScanState>(ScanState.Idle)

    fun lookUp(code: String, onFound: (foodId: Int) -> Unit) = viewModelScope.launch {
        state.value = ScanState.Looking(code)
        try {
            products.findByBarcode(code)?.let {
                state.value = ScanState.Idle
                onFound(it)
                return@launch
            }
            when (val result = withContext(Dispatchers.IO) { OpenFoodFacts.lookup(code) }) {
                is OpenFoodFacts.Lookup.Found -> {
                    val id = products.addProduct(result.product)
                    state.value = ScanState.Idle
                    onFound(id)
                }
                is OpenFoodFacts.Lookup.NoNutrition -> state.value = ScanState.NotFound(code, result.name, result.brand)
                OpenFoodFacts.Lookup.NotFound -> state.value = ScanState.NotFound(code, null, null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.value = ScanState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    fun dismiss() {
        state.value = ScanState.Idle
    }
}

/** Opens Google's barcode scanner (Play services; no camera permission needed) for product barcodes. */
fun startBarcodeScan(context: Context, onCode: (String) -> Unit, onError: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
        .enableAutoZoom()
        .build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { barcode -> barcode.rawValue?.let(onCode) }
        // Backing out of the scanner arrives as a cancellation, which needs no message, so only failures report.
        .addOnFailureListener { e -> onError(e.message ?: e.javaClass.simpleName) }
}

/** Progress and outcome dialogs for a scan. [onCreate] starts a custom food remembering the barcode. */
@Composable
fun BarcodeDialogs(vm: BarcodeViewModel, onCreate: (name: String, barcode: String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        ScanState.Idle -> Unit
        is ScanState.Looking -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(stringResource(R.string.scan_looking_up)) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(s.code)
                }
            },
        )
        is ScanState.NotFound -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text(stringResource(R.string.scan_not_found_title)) },
            text = {
                Text(
                    listOfNotNull(s.brand, s.name).joinToString(" · ").let { known ->
                        if (known.isEmpty()) stringResource(R.string.scan_not_found, s.code)
                        else stringResource(R.string.scan_no_nutrition, known)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.dismiss()
                    onCreate(listOfNotNull(s.brand, s.name).joinToString(" "), s.code)
                }) { Text(stringResource(R.string.create_food)) }
            },
            dismissButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.cancel)) } },
        )
        is ScanState.Failed -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text(stringResource(R.string.scan_failed)) },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.ok)) } },
        )
    }
}
