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
    val selectedOption: String = "Tonemap"
)

class EnhancementViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val enhancementExecutor = Executors.newSingleThreadExecutor()
    private lateinit var enhancementClient: EnhancementClient

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
        viewModelScope.launch(Dispatchers.IO) {
            // Reset state for new image
            _uiState.update { it.copy(isLoading = true, originalImage = null, enhancedImage = null) }

            val decodeStartTime = System.currentTimeMillis()
            val originalBitmap = decodeBitmapFromUri(uri, context)
            val decodeLatency = System.currentTimeMillis() - decodeStartTime

            if (originalBitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from URI.")
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            val originalImageInfo = ImageInfo(bitmap = originalBitmap, latency = decodeLatency)
            _uiState.update { it.copy(originalImage = originalImageInfo) }

            enhanceImage(originalBitmap, context)
        }
    }

    fun onOptionSelected(option: String, context: Context) {
        // Prevent re-processing if the option is already selected and we have a result
        if (_uiState.value.selectedOption == option && _uiState.value.enhancedImage != null) return

        _uiState.update { it.copy(selectedOption = option, isLoading = true) }

        val originalBitmap = _uiState.value.originalImage?.bitmap
        if (originalBitmap == null) {
            Log.e(TAG, "Original bitmap is null, cannot enhance.")
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
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

            // Get the original image's decoding latency, default to 0 if it's somehow not available
            val originalDecodeLatency = _uiState.value.originalImage?.latency ?: 0
            val latencyDifference = enhancementLatency - originalDecodeLatency

            _uiState.update {
                it.copy(
                    enhancedImage = ImageInfo(bitmap = enhancedBitmap, latency = latencyDifference),
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
            //true,true,false,true
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