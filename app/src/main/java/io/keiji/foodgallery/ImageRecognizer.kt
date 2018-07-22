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
import android.os.Debug
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.Executors

private const val NUM_WORKERS = 4

class ImageRecognizer(assetManager: AssetManager) : LifecycleObserver {

    companion object {
        val TAG = ImageRecognizer::class.java.simpleName

        // https://github.com/keiji/food_gallery_with_tensorflow
        private val MODEL_FILE_PATH = "food_model_quant_nnapi_3ch.tflite"

        private val IMAGE_WIDTH = 256
        private val IMAGE_HEIGHT = 256
        private val IMAGE_CHANNEL = 4

        val IMAGE_BYTES_LENGTH = IMAGE_WIDTH * IMAGE_HEIGHT * IMAGE_CHANNEL
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
        it.setUseNNAPI(true)
    }

    data class Request(val bitmap: Bitmap, val callbak: Callback)

    val channel: Channel<Request> = Channel()

    val workers = (1..NUM_WORKERS).map {
        coroutineScope.launch {
            val tfInference = Interpreter(
                    model,
                    options)

            val imageByteBuffer = ByteBuffer
                    .allocateDirect(IMAGE_BYTES_LENGTH)
                    .order(ByteOrder.nativeOrder())

            val inputBuffer = ByteBuffer
                    .allocateDirect(IMAGE_WIDTH * IMAGE_HEIGHT * 3)
                    .order(ByteOrder.nativeOrder())

            val resultBuffer = ByteBuffer
                    .allocateDirect(1)
                    .order(ByteOrder.nativeOrder())

            for (request in channel) {
                val (bitmap, callbak) = request
                if (bitmap.isRecycled) {
                    continue
                }

                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_WIDTH, IMAGE_HEIGHT, false)

                imageByteBuffer.rewind()
                scaledBitmap.copyPixelsToBuffer(imageByteBuffer)

                imageByteBuffer.rewind()

                for (index in 0 until IMAGE_BYTES_LENGTH) {
                    if ((index % 4) != 3) {
                        inputBuffer.put(imageByteBuffer[index])
                    }
                }

                // https://github.com/CyberAgent/android-gpuimage/issues/24
                if (scaledBitmap !== bitmap) {
                    scaledBitmap.recycle()
                }

                inputBuffer.rewind()
                resultBuffer.rewind()

                val start = Debug.threadCpuTimeNanos()
                tfInference.run(inputBuffer, resultBuffer)
                resultBuffer.rewind()

                val elapsed = Debug.threadCpuTimeNanos() - start
                val confidence = resultBuffer.get().toInt().and(0xFF) / 255.0F

                resultBuffer.clear()
                inputBuffer.clear()

                callbak.onRecognize(confidence)
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
        fun onRecognize(confidence: Float)
    }
}
