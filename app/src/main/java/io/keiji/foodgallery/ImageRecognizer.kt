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
import org.tensorflow.contrib.android.TensorFlowInferenceInterface

class ImageRecognizer(assetManager: AssetManager) {

    companion object {
        val TAG = ImageRecognizer::class.java.simpleName

        // https://github.com/keiji/food_gallery_with_tensorflow
        private val MODEL_FILE_PATH = "food_model_4ch.pb"

        private val IMAGE_WIDTH = 128
        private val IMAGE_HEIGHT = 128
        private val IMAGE_CHANNEL = 4

        val IMAGE_BYTES_LENGTH = IMAGE_WIDTH * IMAGE_HEIGHT * IMAGE_CHANNEL

        fun resizeToPreferSize(bitmap: Bitmap): Bitmap {
            return Bitmap.createScaledBitmap(bitmap, IMAGE_WIDTH, IMAGE_HEIGHT, false)
        }
    }

    val tfInference: TensorFlowInferenceInterface = TensorFlowInferenceInterface(
            assetManager.open(MODEL_FILE_PATH))

    val resultArray = FloatArray(1)

    fun recognize(imageByteArray: ByteArray): Float {
        val start = Debug.threadCpuTimeNanos()

        tfInference.feed("input", imageByteArray, imageByteArray.size.toLong())
        tfInference.run(arrayOf("result"))
        tfInference.fetch("result", resultArray)

        val elapsed = Debug.threadCpuTimeNanos() - start
        Log.d(TAG, "Elapsed: %,3d ns".format(elapsed))

        return resultArray[0]
    }

    fun stop() {
        tfInference.close()
    }
}
