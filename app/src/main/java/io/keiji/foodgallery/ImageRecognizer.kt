package io.keiji.foodgallery

/*
Copyright 2018-2019 Keiji ARIYAMA

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.Executors

private const val NUM_WORKERS = 4

@ExperimentalCoroutinesApi
class ImageRecognizer(assetManager: AssetManager) : LifecycleObserver {

    companion object {
        val TAG = ImageRecognizer::class.java.simpleName

        // https://github.com/keiji/food_gallery_with_tensorflow
        private val MODEL_FILE_PATH = "food_model_quant_3ch.tflite"

        private val IMAGE_WIDTH = 128
        private val IMAGE_HEIGHT = 128
        private val IMAGE_CHANNEL = 3
    }

    private val dispatcher = Executors
            .newFixedThreadPool(NUM_WORKERS)
            .asCoroutineDispatcher()
    private val coroutineScope = CoroutineScope(dispatcher)

    @Throws(IOException::class)
    private fun loadModelFile(assets: AssetManager, modelFileName: String): ByteBuffer {
        val fileDescriptor = assets.openFd(modelFileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)

        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    val model = loadModelFile(assetManager, MODEL_FILE_PATH)

    val options = Interpreter.Options().also {
        //        it.setUseNNAPI(true)
    }

    data class Request(
            val bitmap: Bitmap,
            val callbak: Callback
    ) {
        var isCanceled = false

        fun cancel() {
            isCanceled = true
        }
    }

    val channel: Channel<Request> = Channel()

    val workers = (1..NUM_WORKERS).map {
        coroutineScope.launch {
            val tfInference = Interpreter(
                    model,
                    options)

            val resizedImageBuffer = ByteBuffer
                    .allocate(IMAGE_WIDTH * IMAGE_WIDTH * 4) // 4 means channel

            val inputBuffer = ByteBuffer
                    .allocateDirect(IMAGE_WIDTH * IMAGE_WIDTH * IMAGE_CHANNEL * 4) // 4 means float
                    .order(ByteOrder.nativeOrder())

            val resultBuffer = ByteBuffer
                    .allocateDirect(4)
                    .order(ByteOrder.nativeOrder())

            for (request in channel) {
                if (request.isCanceled) {
                    continue
                }

                val (bitmap, callbak) = request
                if (bitmap.isRecycled) {
                    continue
                }

                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_WIDTH, IMAGE_HEIGHT, true)

                resizedImageBuffer.rewind()
                scaledBitmap.copyPixelsToBuffer(resizedImageBuffer)

                // https://github.com/CyberAgent/android-gpuimage/issues/24
                if (scaledBitmap !== bitmap) {
                    scaledBitmap.recycle()
                }

                resizedImageBuffer.rewind()

                inputBuffer.rewind()
                for (index in (0 until IMAGE_WIDTH * IMAGE_HEIGHT * 4)) { // 4 means channel
                    if ((index % 4) < 3) {
                        inputBuffer.putFloat(resizedImageBuffer[index].toInt().and(0xFF).toFloat())
                    }
                }

                inputBuffer.rewind()
                resultBuffer.rewind()

                val start = System.nanoTime()
                tfInference.run(inputBuffer, resultBuffer)
                val elapsed = System.nanoTime() - start
                Log.d(TAG, "elapsed: $elapsed")

                resultBuffer.rewind()
                val confidence = resultBuffer.float

                resultBuffer.clear()
                inputBuffer.clear()

                if (request.isCanceled) {
                    continue
                }

                request.callbak.onRecognize(confidence)
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate() {
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        workers.map { it.cancel() }
        channel.close()
        dispatcher.close()

        coroutineScope.cancel()
    }

    interface Callback {
        suspend fun onRecognize(confidence: Float)
    }
}
