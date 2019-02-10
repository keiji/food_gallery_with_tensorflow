package io.keiji.foodgallery

/*
Copyright 2018 Keiji ARIYAMA

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
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class ImageRecognizer(assetManager: AssetManager) {

    companion object {
        val TAG = ImageRecognizer::class.java.simpleName

        // https://github.com/keiji/food_gallery_with_tensorflow
        private val MODEL_FILE_PATH = "food_model_quant_4ch.tflite"

        private val IMAGE_WIDTH = 128
        private val IMAGE_HEIGHT = 128
        private val IMAGE_CHANNEL = 4

        val IMAGE_BYTES_LENGTH = IMAGE_WIDTH * IMAGE_HEIGHT * IMAGE_CHANNEL

        fun resizeToPreferSize(bitmap: Bitmap): Bitmap {
            return Bitmap.createScaledBitmap(bitmap, IMAGE_WIDTH, IMAGE_HEIGHT, false)
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(assets: AssetManager, modelFileName: String): ByteBuffer {
        val fileDescriptor = assets.openFd(modelFileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)

        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    val options = Interpreter.Options().also {
        //        it.setUseNNAPI(true)
    }
    val tfInference: Interpreter = Interpreter(
            loadModelFile(assetManager, MODEL_FILE_PATH),
            options)

    val resultArray = Array(1, { FloatArray(1) })

    fun recognize(imageByteBuffer: ByteBuffer): Float {

        val start = Debug.threadCpuTimeNanos()
        tfInference.run(imageByteBuffer, resultArray)
        imageByteBuffer.rewind()

        val elapsed = Debug.threadCpuTimeNanos() - start
        Log.d(TAG, "Elapsed: %,3d ns".format(elapsed))

        return resultArray[0][0].toInt().and(0xFF) / 255.0F
    }

    fun stop() {
        tfInference.close()
    }
}
