package com.android.imagesuperresolution

import android.content.Context
import android.graphics.Bitmap
import com.google.android.libraries.mediacommon.effect.enhancement.EnhancementCallback
import com.google.android.libraries.mediacommon.effect.enhancement.EnhancementClient
import com.google.android.libraries.mediacommon.effect.enhancement.EnhancementOptions
import com.google.android.libraries.mediacommon.effect.enhancement.EnhancementSession
import com.google.android.libraries.mediacommon.effect.enhancement.EnhancementSessionCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class EnhancementFailedException(val errorCode: Int, message: String) : Exception(message)

/**
 * A modern coroutine wrapper for the enhancement process.
 *
 * This suspend function encapsulates the entire callback-based process of creating a session,
 * processing a bitmap, and handling success or failure.
 *
 * @param context The application context.
 * @param bitmap The input bitmap to enhance.
 * @param options The enhancement options to apply.
 * @param executor The executor on which to run the callbacks.
 * @return The enhanced [Bitmap] on success.
 * @throws [EnhancementFailedException] if any step of the process fails.
 */
suspend fun EnhancementClient.processBitmapAsync(
    context: Context,
    bitmap: Bitmap,
    options: EnhancementOptions,
    executor: Executor
): Bitmap = suspendCancellableCoroutine { continuation ->
    var session: EnhancementSession? = null

    val sessionCallback = object : EnhancementSessionCallback {
        override fun onSessionCreated(session: EnhancementSession) {
           // session = session

            // When the coroutine is cancelled, ensure we release the session.
            continuation.invokeOnCancellation { session.release() }

            val processingCallback = object : EnhancementCallback {
                override fun onBitmapProcessed(outputBitmap: Bitmap) {
                    continuation.resume(outputBitmap)
                    session.release()
                }

                override fun onError(statusCode: Int) {
                    continuation.resumeWithException(
                        EnhancementFailedException(statusCode, "Processing failed in onBitmapProcessed. $statusCode")
                    )
                    session.release()
                }

                override fun onSurfaceProcessed(timestamp: Long) { /* Not used in bitmap flow */ }
            }

            session.process(bitmap, options, processingCallback)
        }

        override fun onSessionCreationFailed(errorCode: Int) {
            continuation.resumeWithException(
                EnhancementFailedException(errorCode, "Session creation failed $errorCode")
            )
        }

        override fun onSessionDestroyed() {
            session = null
            if (continuation.isActive) {
                continuation.resumeWithException(
                    EnhancementFailedException(-1, "Session was destroyed unexpectedly. ErrorCode -1")
                )
            }
        }

        override fun onSessionDisconnected(statusCode: Int) {
            session = null
            if (continuation.isActive) {
                continuation.resumeWithException(
                    EnhancementFailedException(statusCode, "Session disconnected unexpectedly. $statusCode")
                )
            }
        }
    }

    // Start the entire process by creating a session
    this.createSession(context, options, sessionCallback, executor)
}
