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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.keiji.foodgallery.databinding.ListItemImageBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.lang.ref.WeakReference

private const val CONFIDENCE_THRESHOLD = 0.8F
private const val ALPHA_IS_FOOD = 1.0F
private const val ALPHA_IS_NOT_FOOD = 0.25F

@ExperimentalCoroutinesApi
class MainAdapter(
        val context: Context,
        val lifecycleOwner: LifecycleOwner,
        val imageRecognizerChannel: Channel<ImageRecognizer.Request>
) : RecyclerView.Adapter<MainAdapter.Holder>(), LifecycleObserver {

    companion object {
        val TAG = MainAdapter::class.java.simpleName
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private lateinit var binding: ListItemImageBinding

    private val mediaPathList = ArrayList<String>()

    private class DiffCallback(private val oldList: List<String>, private val newList: List<String>) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    fun setItems(itemList: List<String>) {
        val diffResult = DiffUtil.calculateDiff(DiffCallback(mediaPathList, itemList))
        mediaPathList.also {
            it.clear()
            it.addAll(itemList)
        }
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int {
        return mediaPathList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        binding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.list_item_image,
                parent,
                false
        )

        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(mediaPathList[position])
    }

    override fun onViewRecycled(holder: Holder) {
        super.onViewRecycled(holder)
        holder.recycle()
    }

    inner class Holder(val binding: ListItemImageBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(path: String) {
            binding.viewModel = ItemListBitmapViewModel(coroutineScope, imageRecognizerChannel, path)
            binding.lifecycleOwner = lifecycleOwner
        }

        fun recycle() {
            binding.imageView.setImageDrawable(null)
            binding.viewModel?.recycle()
        }
    }

    class ItemListBitmapViewModel(
            private val coroutineScope: CoroutineScope,
            private val channel: Channel<ImageRecognizer.Request>,
            private val path: String
    ) {
        val photoPrediction = MutableLiveData<Float>()
        val progressVisibility = MutableLiveData<Int>()

        private var bitmap: Bitmap? = null

        var thumbnailLoadJob: Job? = null
        var recognizeRequest: ImageRecognizer.Request? = null

        fun recycle() {
            thumbnailLoadJob?.cancel()
            thumbnailLoadJob = null

            recognizeRequest?.cancel()
            recognizeRequest = null

            bitmap?.recycle()
            bitmap = null
        }

        val options: BitmapFactory.Options = BitmapFactory.Options().apply {
            inSampleSize = 2
        }

        val callback = object : ImageRecognizer.Callback {
            override suspend fun onRecognize(confidence: Float) = withContext(Dispatchers.Main) {
                setConfidence(confidence)
            }
        }

        fun startImageLoading(viewModel: ItemListBitmapViewModel,
                              imageViewRef: WeakReference<ImageView>) {

            viewModel.apply {
                bitmap?.let {
                    imageViewRef.get()?.setImageBitmap(it)
                    return
                }

                progressVisibility.postValue(View.VISIBLE)

                thumbnailLoadJob?.cancel()
                thumbnailLoadJob = coroutineScope.launch {
                    val image = BitmapFactory.decodeFile(path, options)
                    bitmap = image

                    withContext(Dispatchers.Main) {
                        imageViewRef.get()?.setImageBitmap(image)
                    }

                    val request = ImageRecognizer.Request(image, callback)
                    recognizeRequest = request
                    channel.send(request)
                }
            }
        }

        private fun setConfidence(confidence: Float) {
            val alpha = if (confidence > CONFIDENCE_THRESHOLD) {
                ALPHA_IS_FOOD
            } else {
                ALPHA_IS_NOT_FOOD
            }

            photoPrediction.postValue(alpha)
            progressVisibility.postValue(View.GONE)
        }
    }
}
