package com.android.imagesuperresolution

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.Size
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.google.android.libraries.mediacommon.effect.enhancement.EnhancementCallback
import com.google.android.libraries.mediacommon.effect.enhancement.EnhancementClient
import com.google.android.libraries.mediacommon.effect.enhancement.EnhancementOptions
import com.google.android.libraries.mediacommon.effect.enhancement.EnhancementSession
import com.google.android.libraries.mediacommon.effect.enhancement.EnhancementSessionCallback
import com.google.android.libraries.mediacommon.effect.enhancement.constants.EnhancementMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.concurrent.Executors

private const val TAG = "ImageComparisonScreen"

data class ImageInfo(
    val bitmap: Bitmap? = null,
    val latency: Long? = null,
    val qualityScore: Int? = null
)

@Composable
fun ImageComparisonScreen(devicePosture: DevicePosture) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var originalImageInfo by remember { mutableStateOf<ImageInfo?>(null) }
    var enhancedImageInfo by remember { mutableStateOf<ImageInfo?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val processingOptions = listOf("Denoise", "Deblur", "Tonemap", "Upscale")
    var selectedOption by remember { mutableStateOf(processingOptions.first()) }

    val enhancementExecutor = remember { Executors.newSingleThreadExecutor() }
    val enhancementClient = remember(enhancementExecutor) {
        EnhancementClient.getInstance(enhancementExecutor)
    }
    var enhancementSession by remember { mutableStateOf<EnhancementSession?>(null) }

    DisposableEffect(enhancementClient) {
        Log.d(TAG, "Binding EnhancementClient")
        enhancementClient.bind()
        onDispose {
            Log.d(TAG, "Disposing effects: Releasing session and unbinding client.")
            enhancementSession?.release()
            enhancementClient.unbind()
        }
    }

    val singlePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                selectedImageUri = uri
                originalImageInfo = null
                enhancedImageInfo = null
            }
        }
    )

    LaunchedEffect(selectedImageUri, selectedOption) {
        selectedImageUri?.let { uri ->
            isLoading = true
            var processingStartTime: Long = 0

            coroutineScope.launch(Dispatchers.IO) {
                val loadedBitmap = decodeBitmapFromUri(uri, context);

                withContext(Dispatchers.Main) {
                    originalImageInfo = ImageInfo(bitmap = loadedBitmap)
                    Log.d(TAG, " Original image loaded")
                }

                if (loadedBitmap == null) {
                    Log.d(TAG, " loadedBitmap = null")
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        Log.d(TAG, " isLoading = false ")
                    }
                    return@launch
                }

                if (!EnhancementClient.isEnhancementSupported()) {
                    Log.e(TAG, "Enhancement is not supported on this device.")
                    enhancedImageInfo = ImageInfo(bitmap = loadedBitmap) // Fallback
                    withContext(Dispatchers.Main) { isLoading = false }
                    return@launch
                }

                Log.d(TAG, " set options")
                val options = EnhancementOptions(
                    Size(loadedBitmap.width, loadedBitmap.height),
                    EnhancementMode.BITMAP,
                    tonemapping = false,
                    deblurAndDenoisePhoto = true,
                    deblurAndDenoiseVideo = false,
                    upscaling = true
                )

                Log.d(TAG, " start sessionCallback")
                val sessionCallback = object : EnhancementSessionCallback {
                    override fun onSessionCreated(session: EnhancementSession) {
                        Log.d(TAG, "onSessionCreated")
                        enhancementSession = session

                        val processingCallback = object : EnhancementCallback {
                            override fun onBitmapProcessed(bitmap: Bitmap) {
                                val processingTime =
                                    System.currentTimeMillis() - processingStartTime
                                Log.d(
                                    TAG,
                                    "onBitmapProcessed: Success! Latency: ${processingTime}ms"
                                )
                                Log.d(TAG, " outputBitmap is $bitmap")

                                coroutineScope.launch(Dispatchers.Main) {
                                    enhancedImageInfo =
                                        ImageInfo(bitmap = bitmap, latency = processingTime)
                                    isLoading = false
                                }
                            }

                            override fun onError(statusCode: Int) {
                                Log.e(TAG, "Processing onError: $statusCode")
                                coroutineScope.launch(Dispatchers.Main) {
                                    enhancedImageInfo = ImageInfo(bitmap = loadedBitmap) // Fallback
                                    isLoading = false
                                }
                            }

                            override fun onSurfaceProcessed(timestamp: Long) {
                                Log.e(TAG, "onSurfaceProcessed")
                            }
                        }
                        processingStartTime = System.currentTimeMillis()
                        Log.d(TAG, "start session.process")
                        session.process(
                            loadedBitmap, options,
                            processingCallback
                        )
                    }

                    override fun onSessionCreationFailed(errorCode: Int) {
                        Log.e(TAG, "onSessionCreationFailed: error=$errorCode")
                        coroutineScope.launch(Dispatchers.Main) {
                            enhancedImageInfo = ImageInfo(bitmap = loadedBitmap) // Fallback
                            isLoading = false
                        }
                    }

                    override fun onSessionDestroyed() {
                        Log.d(TAG, "onSessionDestroyed")
                        enhancementSession = null
                    }

                    override fun onSessionDisconnected(statusCode: Int) {
                        Log.d(TAG, "onSessionDisconnected: status=$statusCode")
                        enhancementSession = null
                    }
                }
                Log.d(TAG, "start createSession")
                enhancementClient.createSession(
                    context,
                    options,
                    sessionCallback,
                    enhancementExecutor
                )
            }
        }
    }

    Scaffold { paddingValues ->
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

            Button(onClick = {
                singlePhotoPickerLauncher.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            }) {
                Text("Select Image from Gallery")
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(processingOptions) { option ->
                    OutlinedButton(
                        onClick = { selectedOption = option },
                        shape = RoundedCornerShape(50),
                        colors = if (selectedOption == option) ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text(option)
                    }
                }
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
                        imageInfo = originalImageInfo,
                        modifier = imageContainerModifier
                    )
                    ProcessingImageDisplay(
                        title = "Enhanced",
                        imageInfo = enhancedImageInfo,
                        isLoading = isLoading,
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
                        imageInfo = originalImageInfo,
                        modifier = imageContainerModifier
                    )
                    ProcessingImageDisplay(
                        title = "Enhanced",
                        imageInfo = enhancedImageInfo,
                        isLoading = isLoading,
                        modifier = imageContainerModifier
                    )
                }
            }
        }
    }
}

private fun decodeBitmapFromUri(uri: Uri, context: Context): Bitmap? {
    var inputStream: InputStream? = null

    inputStream = context.contentResolver.openInputStream(uri)
    Log.d(TAG, "decodeBitmapFromUri: uri=$uri")
    return BitmapFactory.decodeStream(inputStream)
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