package com.android.imagesuperresolution

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.Size
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.mediacommon.effect.enhancement.EnhancementClient
import com.google.android.libraries.mediacommon.effect.enhancement.EnhancementOptions
import com.google.android.libraries.mediacommon.effect.enhancement.constants.EnhancementMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private const val TAG = "EnhancementViewModel"

// Data class to hold all information about an image
data class ImageInfo(
    val bitmap: Bitmap? = null,
    val latency: Long? = null,
    val qualityScore: Int? = null
)

// Defines the state of the UI
data class UiState(
    val originalImage: ImageInfo? = null,
    val enhancedImage: ImageInfo? = null,
    val isLoading: Boolean = false,
    val selectedOption: String = "Tonemap" // Reverted to single String
)

class EnhancementViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val enhancementExecutor = Executors.newSingleThreadExecutor()
    private lateinit var enhancementClient: EnhancementClient
    private var enhancementJob: Job? = null

    fun initialize(context: Context) {
        enhancementClient = EnhancementClient.getInstance(enhancementExecutor)
        enhancementClient.bind()
        Log.d(TAG, "EnhancementClient initialized and bound.")
    }

    override fun onCleared() {
        if (::enhancementClient.isInitialized) {
            enhancementClient.unbind()
        }
        enhancementExecutor.shutdown()
        Log.d(TAG, "EnhancementClient unbound and executor shut down.")
        super.onCleared()
    }

    fun onImageSelected(uri: Uri, context: Context) {
        enhancementJob?.cancel()
        enhancementJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, originalImage = null, enhancedImage = null) }

            val originalBitmap = decodeBitmapFromUri(uri, context)

            if (originalBitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from URI.")
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            // The original image has no enhancement latency.
            val originalImageInfo = ImageInfo(bitmap = originalBitmap, latency = null)
            _uiState.update { it.copy(originalImage = originalImageInfo) }

            enhanceImage(originalBitmap, context)
        }
    }

    fun onOptionSelected(option: String, context: Context) {
        // Reverted to single-select logic
        if (_uiState.value.selectedOption == option) return

        _uiState.update { it.copy(selectedOption = option) }

        val originalBitmap = _uiState.value.originalImage?.bitmap
        if (originalBitmap == null) {
            return
        }

        enhancementJob?.cancel()
        enhancementJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            enhanceImage(originalBitmap, context)
        }
    }

    private suspend fun enhanceImage(bitmap: Bitmap, context: Context) {
        if (!EnhancementClient.isEnhancementSupported()) {
            Log.e(TAG, "Enhancement is not supported on this device.")
            _uiState.update { it.copy(enhancedImage = ImageInfo(bitmap = bitmap), isLoading = false) }
            return
        }

        val options = getEnhancementOptionsFor(bitmap, _uiState.value.selectedOption)

        try {
            val enhancementStartTime = System.currentTimeMillis()
            val enhancedBitmap = enhancementClient.processBitmapAsync(context, bitmap, options, enhancementExecutor)
            val enhancementLatency = System.currentTimeMillis() - enhancementStartTime

            // Store the pure enhancement latency, not a difference.
            _uiState.update {
                it.copy(
                    enhancedImage = ImageInfo(bitmap = enhancedBitmap, latency = enhancementLatency),
                    isLoading = false
                )
            }
        } catch (e: EnhancementFailedException) {
            Log.e(TAG, "Enhancement failed with code ${e.errorCode}: ${e.message}", e)
            _uiState.update { it.copy(enhancedImage = ImageInfo(bitmap = bitmap), isLoading = false) } // Fallback
        }
    }

    private fun getEnhancementOptionsFor(bitmap: Bitmap, option: String): EnhancementOptions {
        return EnhancementOptions(
            Size(bitmap.width, bitmap.height),
            EnhancementMode.BITMAP,
            tonemapping = option == "Tonemap",
            deblurAndDenoisePhoto = option == "Deblur & DeNoise",
            deblurAndDenoiseVideo = false, // Not used for photos
            upscaling = option == "Upscale"
        )
    }

    private fun decodeBitmapFromUri(uri: Uri, context: Context): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding bitmap from URI", e)
            null
        }
    }
}