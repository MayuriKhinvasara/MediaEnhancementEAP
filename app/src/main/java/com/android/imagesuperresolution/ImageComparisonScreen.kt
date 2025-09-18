package com.android.imagesuperresolution

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import java.text.DecimalFormat


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageComparisonScreen(
    devicePosture: DevicePosture,
    enhancementViewModel: EnhancementViewModel = viewModel() // Get the ViewModel instance
) {
    val context = LocalContext.current
    val uiState by enhancementViewModel.uiState.collectAsState()

    // Initialize the ViewModel once when the composable enters the screen
    DisposableEffect(Unit) {
        enhancementViewModel.initialize(context)
        onDispose {}
    }

    val singlePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            uri?.let {
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
            // --- Header Section --- //
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

            Spacer(modifier = Modifier.height(12.dp))

            // Control row with chips and Enhance button
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                Button(onClick = { enhancementViewModel.enhanceImage(context) }) {
                    Text("AI Enhance")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Image Content Section --- //
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