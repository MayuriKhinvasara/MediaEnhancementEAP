package com.android.imagesuperresolution

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.media.effect.enhancement.EnhancementMode
import java.text.DecimalFormat

@RequiresApi(Build.VERSION_CODES.R)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageComparisonScreen(
    devicePosture: DevicePosture,
    enhancementViewModel: EnhancementViewModel
) {
    val context = LocalContext.current
    val uiState by enhancementViewModel.uiState.collectAsState()

    val singlePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            uri?.let {
                enhancementViewModel.setEnhancementMode(EnhancementMode.BITMAP)
                enhancementViewModel.onImageSelected(it, context)
            }
        }
    )

    val processingOptions = listOf("Tonemap", "Deblur & DeNoise", "Upscale")

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Button(onClick = {
                singlePhotoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }) {
                Text("Select Image from Gallery")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Module Status: ${uiState.moduleStatus}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(processingOptions) { option ->
                        FilterChip(
                            selected = option in uiState.selectedOptions,
                            onClick = { enhancementViewModel.onOptionSelected(option) },
                            label = { Text(option) }
                        )
                    }
                }
                Spacer(modifier = Modifier.padding(start = 8.dp))
                Button(
                    onClick = { enhancementViewModel.enhanceImage() },
                    enabled = uiState.isModuleReady && !uiState.isLoading && uiState.originalImage?.bitmap != null
                ) {
                    Text("AI Enhance")
                }
            }

            if (uiState.isModuleInstalling) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        progress = { uiState.moduleInstallProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Downloading Enhancement module only for the first time (${uiState.moduleInstallProgress}%)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (uiState.moduleInstallError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.moduleInstallError ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (uiState.enhancementError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.enhancementError ?: "Unknown enhancement error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val imageContainerModifier = Modifier.weight(1f)

            if (devicePosture != DevicePosture.NORMAL) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ImageDisplay(
                        title = "Original",
                        imageInfo = uiState.originalImage,
                        modifier = imageContainerModifier
                    )
                    ProcessingImageDisplay(
                        title = "Enhanced",
                        imageInfo = uiState.enhancedImage,
                        isLoading = uiState.isLoading,
                        hasError = uiState.enhancementError != null,
                        modifier = imageContainerModifier
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ImageDisplay(
                        title = "Original",
                        imageInfo = uiState.originalImage,
                        modifier = imageContainerModifier
                    )
                    ProcessingImageDisplay(
                        title = "Enhanced",
                        imageInfo = uiState.enhancedImage,
                        isLoading = uiState.isLoading,
                        hasError = uiState.enhancementError != null,
                        modifier = imageContainerModifier
                    )
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
            Text(text = "Select an Image", modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun ProcessingImageDisplay(
    title: String,
    imageInfo: ImageInfo?,
    isLoading: Boolean,
    hasError: Boolean,
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
        } else if (hasError) {
            Text(
                text = "Enhancement Failed",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center)
            )
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
            Text(text = "Enhanced Image", modifier = Modifier.align(Alignment.Center))
        }
    }
}

// Helper function to format byte count into KB or MB
private fun formatMemorySize(bytes: Int): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val decimalFormat = DecimalFormat("#.##")
    return when {
        mb >= 1 -> "${decimalFormat.format(mb)} MB"
        else -> "${decimalFormat.format(kb)} KB"
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
            .padding(1.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 2.dp, vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        if (bitmap != null) {
            val memorySize = formatMemorySize(bitmap.byteCount)
            Text(
                text = "$memorySize, ${bitmap.width}x${bitmap.height}",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (latency != null && title != "Original") {
            Text(
                text = "AI Latency: ${latency}ms",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (quality != null) {
            Text(
                text = "Quality: $quality/100",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
