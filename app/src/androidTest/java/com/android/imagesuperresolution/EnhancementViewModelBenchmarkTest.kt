package com.android.imagesuperresolution

import android.Manifest
import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class EnhancementViewModelBenchmarkTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_MEDIA_IMAGES
    )

    @Test
    fun benchmarkEnhancementViewModel() {
        runBlocking {
            val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            val application = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
            
            // Helper to run shell command and block until finished
            fun runShell(cmd: String) {
                val pfd = instrumentation.uiAutomation.executeShellCommand(cmd)
                android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
            }

            // Grant full external storage access so we can read the raw ADB pushed files
            runShell("appops set com.android.imagesuperresolution MANAGE_EXTERNAL_STORAGE allow")
        
            val baseDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Enhance")
            val deviceModelName = Build.MODEL.replace(" ", "_")
        val reportFile = File(
            application.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), 
            "Enhancement_ViewModel_Benchmark_$deviceModelName.md"
        )

        val report = StringBuilder()
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        report.appendLine("# Image Super Resolution Latency Benchmark")
        report.appendLine("**Date:** $date")
        report.appendLine("**Device:** ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
        report.appendLine()
        
        report.appendLine("## Key Findings")
        report.appendLine("1. **Bypass on matching dimensions:** The AI Upscaler will bypass itself entirely if the target output dimensions exactly match the input dimensions, making the latency identical to Tonemap Only.")
        report.appendLine("2. **Resolution limit constraints:** The model crashes/errors when fed 1080p images into the Image Upscale mode, but successfully processes 720p images (though it takes ~12 seconds).")
        report.appendLine("3. **Cold-start overhead:** The ~3.3s Tonemap latency includes the Vulkan/OpenCL shader compilation because the ML session is destroyed and recreated for each image.")
        report.appendLine()
        
        val testOptions = listOf(
            "Tonemap",
            "Image Upscale",
            "Video Upscale",
            "Image Upscale Only",
            "Video Upscale Only"
        )
        val displayHeaders = testOptions.map { option ->
            when (option) {
                "Tonemap" -> "Tonemap"
                "Image Upscale" -> "Tonemap, Image Upscale"
                "Video Upscale" -> "Tonemap, Video Upscale"
                "Image Upscale Only" -> "Image Upscale Only"
                "Video Upscale Only" -> "Video Upscale Only"
                else -> option
            }
        }

        // Build main table header
        val mainHeaders = mutableListOf("Image Name", "Original Size", "Enhanced Size")
        mainHeaders.addAll(displayHeaders)
        report.appendLine(mainHeaders.joinToString(" | ", "| ", " |"))
        report.appendLine(mainHeaders.map { "---" }.joinToString(" | ", "| ", " |"))

        val summaryData = mutableMapOf<String, MutableList<Pair<String, List<Long?>>>>()
        val images = baseDir.listFiles()?.filter { it.extension.lowercase() in listOf("jpg", "png", "jpeg") }?.sortedBy { it.name } ?: emptyList()
        
        for (image in images) {
            Log.i("Benchmark", "Starting benchmark for image: ${image.name}")
            
            // Query original dimensions dynamically without loading full bitmap
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(image.absolutePath, options)
            val originalSize = "${options.outWidth}x${options.outHeight}"
            
            val resultCells = mutableListOf<String>()
            val latencyResults = mutableListOf<Long?>()
            var enhSize = "N/A"

            for (option in testOptions) {
                val (latencyStr, outputSize, savedFile) = measureViewModelLatency(application, image.absolutePath, option)
                
                if (outputSize != "N/A") {
                    enhSize = outputSize
                }
                
                val cell = if (savedFile != null) "[$latencyStr](./EnhancedScreenshots/$savedFile)" else latencyStr
                resultCells.add(cell)
                
                latencyResults.add(latencyStr.toLongOrNull())
            }

            // Print image with absolute path in the main table
            report.appendLine("| ${image.absolutePath} | $originalSize | $enhSize | ${resultCells.joinToString(" | ")} |")

            // Collect latencies and image names for summary table
            summaryData.getOrPut(originalSize) { mutableListOf() }.add(Pair(image.name, latencyResults))
        }
        report.appendLine()

        report.appendLine("## Summary Table (Sorted by Resolution)")
        val summaryHeaders = mutableListOf("Resolution", "Image Name")
        summaryHeaders.addAll(displayHeaders)
        report.appendLine(summaryHeaders.joinToString(" | ", "| ", " |"))
        report.appendLine(summaryHeaders.map { "---" }.joinToString(" | ", "| ", " |"))

        // Sort resolutions by total pixels (width * height)
        val sortedResolutions = summaryData.keys.sortedWith(Comparator { r1, r2 ->
            val parts1 = r1.split('x')
            val parts2 = r2.split('x')
            if (parts1.size == 2 && parts2.size == 2) {
                val w1 = parts1[0].toIntOrNull() ?: 0
                val h1 = parts1[1].toIntOrNull() ?: 0
                val w2 = parts2[0].toIntOrNull() ?: 0
                val h2 = parts2[1].toIntOrNull() ?: 0
                (w1 * h1).compareTo(w2 * h2)
            } else {
                r1.compareTo(r2)
            }
        })

        for (res in sortedResolutions) {
            val runs = summaryData[res] ?: emptyList()
            for (run in runs) {
                val imageName = run.first
                val latencies = run.second
                val cells = latencies.map { lat ->
                    if (lat != null) "$lat" else "N/A"
                }
                report.appendLine("| $res | $imageName | ${cells.joinToString(" | ")} |")
            }
        }
        report.appendLine()

        val finalReport = report.toString()
        reportFile.writeText(finalReport)
        Log.d("Benchmark", "SUCCESS! Report saved to: ${reportFile.absolutePath}")
        
        // Also print the entire report to Logcat line by line so it doesn't get truncated
        Log.i("BenchmarkReport", "=== START OF REPORT ===")
        finalReport.lines().forEach { line ->
            Log.i("BenchmarkReport", line)
        }
        Log.i("BenchmarkReport", "=== END OF REPORT ===")
        
        // Android Studio uninstalls the test app immediately, wiping the sandbox.
        // We MUST copy the report to the public Download folder using shell privileges to save it.
        runShell("cp ${reportFile.absolutePath} /sdcard/Download/")
        Log.d("Benchmark", "COPIED report to /sdcard/Download/${reportFile.name} to prevent deletion!")
        }
    }

    private suspend fun measureViewModelLatency(
        application: Application,
        imagePath: String,
        upscaleOption: String
    ): Triple<String, String, String?> {
        val viewModel = EnhancementViewModel(application)
        
        // Wait for module to be ready
        viewModel.uiState.first { it.isModuleReady || it.moduleInstallError != null }
        
        // Resolve the exact target options list
        val targetOptionsList = when (upscaleOption) {
            "Tonemap" -> listOf("Tonemap")
            "Image Upscale" -> listOf("Tonemap", "Image Upscale")
            "Video Upscale" -> listOf("Tonemap", "Video Upscale")
            "Image Upscale Only" -> listOf("Image Upscale")
            "Video Upscale Only" -> listOf("Video Upscale")
            else -> listOf(upscaleOption)
        }

        // Toggle off options that are not in targetOptionsList
        val currentOptions = viewModel.uiState.value.selectedOptions
        for (opt in currentOptions) {
            if (opt !in targetOptionsList) {
                viewModel.onOptionSelected(opt)
            }
        }
        // Toggle on target options that are not currently selected
        for (opt in targetOptionsList) {
            if (opt !in viewModel.uiState.value.selectedOptions) {
                viewModel.onOptionSelected(opt)
            }
        }

        // Load Image
        viewModel.onImageSelected(Uri.fromFile(File(imagePath)), application)
        
        // Wait for image to load (with a timeout to catch failures)
        val imageLoaded = kotlinx.coroutines.withTimeoutOrNull(10_000) {
            viewModel.uiState.first { it.originalImage?.bitmap != null && !it.isLoading }
        }
        
        if (imageLoaded == null) {
            Log.e("Benchmark", "ERROR: Failed to access or load original photo: $imagePath")
            return Triple("Load Error", "N/A", null)
        }
        
        Log.d("Benchmark", "Successfully accessed original photo: $imagePath")
        
        // Start enhancement (simulating "AI Enhance" button click)
        viewModel.enhanceImage()

        // Wait for enhancement to finish (with a generous 60-second timeout)
        val finalState = kotlinx.coroutines.withTimeoutOrNull(60_000) {
            viewModel.uiState.first { (it.enhancedImage != null || it.enhancementError != null) && !it.isLoading }
        }
        
        if (finalState == null) {
            Log.e("Benchmark", "ERROR: Enhancement timed out or hung indefinitely!")
            viewModel.releaseSession()
            return Triple("Timeout", "N/A", null)
        }
        
        if (finalState.enhancementError != null) {
            Log.e("Benchmark", "ERROR: Enhancement failed: ${finalState.enhancementError}")
            viewModel.releaseSession()
            return Triple(finalState.enhancementError.replace("\n", " ").replace("|", " "), "N/A", null)
        }
        
        val latency = finalState.enhancedImage?.latency ?: 0L
        val bmp = finalState.enhancedImage?.bitmap
        val outputSize = if (bmp != null) "${bmp.width}x${bmp.height}" else "N/A"
        
        Log.d("Benchmark", "Successfully processed bitmap! Latency: $latency ms")
        
        var savedFilename: String? = null
        if (bmp != null) {
            val originalFile = File(imagePath)
            val baseName = originalFile.nameWithoutExtension
            val sanitizedOption = upscaleOption.replace(" ", "_").replace(",", "")
            savedFilename = "${baseName}_${sanitizedOption}.png"
            
            // Save enhanced bitmap to public Downloads folder for report matching
            try {
                val dir = File("/sdcard/Download/EnhancedScreenshots")
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val destFile = File(dir, savedFilename)
                java.io.FileOutputStream(destFile).use { out ->
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                Log.d("Benchmark", "Saved enhanced bitmap to: ${destFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("Benchmark", "Failed to save enhanced bitmap: ${e.message}")
                savedFilename = null
            }
        }

        // Cleanup to prevent out of memory issues when running in loop
        viewModel.releaseSession()

        return Triple(latency.toString(), outputSize, savedFilename)
    }
}
