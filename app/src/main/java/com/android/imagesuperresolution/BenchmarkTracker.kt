package com.android.imagesuperresolution

import android.util.Log

object BenchmarkTracker {
    private const val TAG = "BenchmarkTracker"
    private var sessionCreateStart = 0L
    private var sessionCreateEnd = 0L
    private var enhancementStart = 0L
    private var enhancementEnd = 0L
    private var cancelStart = 0L
    private var cancelEnd = 0L
    private var releaseStart = 0L
    private var releaseEnd = 0L

    private var isSessionCreated = false
    private var isEnhanced = false
    private var isCancelled = false
    private var isReleased = false

    private var originalResolution: String? = null
    private var enhancedResolution: String? = null
    private var selectedOptions: String? = null

    @JvmStatic
    fun clear() {
        sessionCreateStart = 0L
        sessionCreateEnd = 0L
        enhancementStart = 0L
        enhancementEnd = 0L
        cancelStart = 0L
        cancelEnd = 0L
        releaseStart = 0L
        releaseEnd = 0L
        isSessionCreated = false
        isEnhanced = false
        isCancelled = false
        isReleased = false
        originalResolution = null
        enhancedResolution = null
        selectedOptions = null
    }

    @JvmStatic
    fun setMetadata(originalRes: String?, enhancedRes: String?, options: String?) {
        originalResolution = originalRes
        enhancedResolution = enhancedRes
        selectedOptions = options
    }

    @JvmStatic
    fun recordSessionCreateStart() {
        clear()
        sessionCreateStart = System.nanoTime()
    }

    @JvmStatic
    fun recordSessionCreateEnd(success: Boolean) {
        sessionCreateEnd = System.nanoTime()
        isSessionCreated = success
        if (!success) {
            printSummary()
        }
    }

    @JvmStatic
    fun recordEnhancementStart() {
        enhancementStart = System.nanoTime()
    }

    @JvmStatic
    fun recordEnhancementEnd(success: Boolean) {
        enhancementEnd = System.nanoTime()
        isEnhanced = success
        printSummary()
    }

    @JvmStatic
    fun recordCancelStart() {
        cancelStart = System.nanoTime()
        isCancelled = true
    }

    @JvmStatic
    fun recordReleaseStart() {
        releaseStart = System.nanoTime()
    }

    @JvmStatic
    fun recordReleaseCallbackReceived() {
        val endTime = System.nanoTime()
        if (isCancelled) {
            cancelEnd = endTime
        } else {
            releaseEnd = endTime
            isReleased = true
        }
        printSummary()
    }

    @JvmStatic
    fun isReleased() = isReleased

    @JvmStatic
    fun isCancelled() = isCancelled

    @JvmStatic
    fun printSummary() {
        val sessionCreateDuration = if (sessionCreateStart > 0 && sessionCreateEnd > 0) {
            (sessionCreateEnd - sessionCreateStart) / 1_000_000.0
        } else null

        val enhancementDuration = if (enhancementStart > 0 && enhancementEnd > 0) {
            (enhancementEnd - enhancementStart) / 1_000_000.0
        } else null

        val cancelDuration = if (cancelStart > 0 && cancelEnd > 0) {
            (cancelEnd - cancelStart) / 1_000_000.0
        } else null

        val releaseDuration = if (releaseStart > 0 && releaseEnd > 0) {
            (releaseEnd - releaseStart) / 1_000_000.0
        } else null

        val totalDuration = if (sessionCreateStart > 0 && enhancementEnd > 0) {
            (enhancementEnd - sessionCreateStart) / 1_000_000.0
        } else null

        val builder = StringBuilder()
        builder.append("\n")
        builder.append("┌──────────────────────────────────────────────────────────────────────────────┐\n")
        builder.append("│                        ENHANCEMENT SESSION BENCHMARKS                        │\n")
        builder.append("├──────────────────────────────────────────────────────────────────────────────┤\n")
        builder.append(String.format("│ Selected Options: %-58s │\n", selectedOptions ?: "None"))
        builder.append(String.format("│ Original Image Resolution: %-49s │\n", originalResolution ?: "Unknown"))
        builder.append(String.format("│ Enhanced Image Resolution: %-49s │\n", enhancedResolution ?: "N/A"))
        builder.append("├──────────────────────────────────────────────┬─────────────────┬─────────────┤\n")
        builder.append(String.format("│ %-44s │ %-15s │ %-11s │\n", "Operation Step", "Duration", "Status"))
        builder.append("├──────────────────────────────────────────────┼─────────────────┼─────────────┤\n")

        if (sessionCreateDuration != null) {
            builder.append(String.format("│ %-44s │ %11.2f ms │ %-11s │\n", "Session Creation", sessionCreateDuration, if (isSessionCreated) "SUCCESS" else "FAILED"))
        } else {
            builder.append(String.format("│ %-44s │ %-15s │ %-11s │\n", "Session Creation", "N/A", "SKIPPED"))
        }

        if (enhancementDuration != null) {
            builder.append(String.format("│ %-44s │ %11.2f ms │ %-11s │\n", "Process Image (Enhancement)", enhancementDuration, if (isEnhanced) "SUCCESS" else "FAILED"))
        } else {
            builder.append(String.format("│ %-44s │ %-15s │ %-11s │\n", "Process Image (Enhancement)", "N/A", "SKIPPED"))
        }

        if (cancelDuration != null) {
            builder.append(String.format("│ %-44s │ %11.2f ms │ %-11s │\n", "Cancellation & Release (Total)", cancelDuration, "SUCCESS"))
        }

        if (releaseDuration != null) {
            builder.append(String.format("│ %-44s │ %11.2f ms │ %-11s │\n", "Session Release (After Completion)", releaseDuration, "SUCCESS"))
        }


        builder.append("└──────────────────────────────────────────────────────────────────────────────┘\n")
        Log.i(TAG, builder.toString())
    }
}
