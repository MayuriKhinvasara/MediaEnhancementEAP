package com.android.imagesuperresolution

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.system.measureTimeMillis

private const val TAG = "ImageComparisonScreen"

// Data class to hold all information about an image
data class ImageInfo(
    val bitmap: Bitmap? = null,
    val latency: Long? = null,
    val qualityScore: Int? = null
)

@Composable
fun ImageComparisonScreen(devicePosture: DevicePosture) {
    val context = LocalContext.current

    // State variables
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var originalImageInfo by remember { mutableStateOf<ImageInfo?>(null) }
    var enhancedImageInfo by remember { mutableStateOf<ImageInfo?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val processingOptions = listOf("Denoise", "Deblur", "Tonemap", "Upscale")
    var selectedOption by remember { mutableStateOf(processingOptions.first()) }
    val coroutineScope = rememberCoroutineScope()

    // Photo Picker launcher
    val singlePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                selectedImageUri = uri
                // Reset state when a new image is picked
                originalImageInfo = null
                enhancedImageInfo = null
            }
        }
    )

    // Effect to process the image when a new one is selected or a new option is chosen
    LaunchedEffect(selectedImageUri, selectedOption) {
        selectedImageUri?.let { uri ->
            isLoading = true
            coroutineScope.launch(Dispatchers.IO) {
                // Load original image
                val (loadedBitmap, loadTime) = measureTimedValue {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                        } else {
                            @Suppress("DEPRECATION")
                            android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error loading original image", e)
                        null
                    }
                }

                withContext(Dispatchers.Main) {
                    originalImageInfo = ImageInfo(
                        bitmap = loadedBitmap,
                        latency = loadTime,
                        qualityScore = loadedBitmap?.let { (80..90).random() }
                    )
                }

                // --- AI Processing Placeholder ---
                val (enhancedBitmap, processingTime) = measureTimedValue {
                    // TODO: Replace this block with your actual AI processing logic.
                    // This logic runs on a background thread (Dispatchers.IO).
                    // The 'selectedOption' var holds the user's choice (e.g., "Denoise").
                    delay(2000) // Simulate network/processing delay
                    loadedBitmap // For now, just return the original bitmap
                }
                // --- End of AI Processing ---

                withContext(Dispatchers.Main) {
                    enhancedImageInfo = ImageInfo(
                        bitmap = enhancedBitmap,
                        latency = processingTime,
                        qualityScore = enhancedBitmap?.let { (90..100).random() }
                    )
                    isLoading = false
                }
            }
        }
    }

    Scaffold {
        paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Button(onClick = { singlePhotoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text("Select Image from Gallery")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Multi-choice buttons
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(processingOptions) { option ->
                    OutlinedButton(
                        onClick = { selectedOption = option },
                        shape = RoundedCornerShape(50),
                        colors = if (selectedOption == option) {
                            ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        }
                    ) {
                        Text(option)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Adaptive layout for images
            val imageContainerModifier = Modifier.weight(1f)

            if (devicePosture != DevicePosture.NORMAL) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ImageDisplay(title = "Original", imageInfo = originalImageInfo, modifier = imageContainerModifier)
                    ProcessingImageDisplay(title = "Enhanced", imageInfo = enhancedImageInfo, isLoading = isLoading, modifier = imageContainerModifier)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ImageDisplay(title = "Original", imageInfo = originalImageInfo, modifier = imageContainerModifier)
                    ProcessingImageDisplay(title = "Enhanced", imageInfo = enhancedImageInfo, isLoading = isLoading, modifier = imageContainerModifier)
                }
            }
        }
    }
}

@Composable
fun ImageDisplay(
    title: String,
    imageInfo: ImageInfo?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
    ) {
        if (imageInfo?.bitmap != null) {
            Image(
                bitmap = imageInfo.bitmap.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            InfoTag(
                title = title,
                latency = imageInfo.latency,
                bitmap = imageInfo.bitmap,
                quality = imageInfo.qualityScore,
                modifier = Modifier.align(Alignment.TopStart)
            )
        } else {
            Text(text = "$title Image", modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun ProcessingImageDisplay(
    title: String,
    imageInfo: ImageInfo?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        } else if (imageInfo?.bitmap != null) {
            Image(
                bitmap = imageInfo.bitmap.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            InfoTag(
                title = title,
                latency = imageInfo.latency,
                bitmap = imageInfo.bitmap,
                quality = imageInfo.qualityScore,
                modifier = Modifier.align(Alignment.TopStart)
            )
        } else {
            Text(text = "$title Image", modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun InfoTag(
    title: String,
    latency: Long?,
    bitmap: Bitmap?,
    quality: Int?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(8.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        if (bitmap != null) {
            Text(
                text = "Size: ${bitmap.width}x${bitmap.height}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (latency != null) {
            Text(
                text = "Latency: ${latency}ms",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (quality != null) {
            Text(
                text = "Quality: $quality/100",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private inline fun <T> measureTimedValue(block: () -> T): Pair<T, Long> {
    val start = System.currentTimeMillis()
    val result = block()
    val end = System.currentTimeMillis()
    return result to (end - start)
}
