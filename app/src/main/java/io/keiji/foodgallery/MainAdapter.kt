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
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.databinding.ObservableField
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import io.keiji.foodgallery.databinding.ListItemImageBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import java.util.concurrent.Executors

private const val CONFIDENCE_THRESHOLD = 0.5F
private const val ALPHA_IS_FOOD = 1.0F
private const val ALPHA_IS_NOT_FOOD = 0.5F

class MainAdapter(
        val context: Context,
        val mediaPathList: MutableLiveData<ArrayList<String>>,
        val imageRecognizerChannel: Channel<ImageRecognizer.Request>
) : RecyclerView.Adapter<MainAdapter.Holder>(), LifecycleObserver {

    companion object {
        val TAG = MainAdapter::class.java.simpleName
    }

    private val dispatcher = Executors
            .newFixedThreadPool(4)
            .asCoroutineDispatcher()
    private val coroutineScope = CoroutineScope(dispatcher)

    private lateinit var binding: ListItemImageBinding

    override fun getItemCount(): Int {
        val mediaPathListSnapshot = mediaPathList.value
        mediaPathListSnapshot ?: return 0

        return mediaPathListSnapshot.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        binding = DataBindingUtil.inflate<ListItemImageBinding>(
                LayoutInflater.from(context),
                R.layout.list_item_image,
                parent,
                false
        ).also {
        }

        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val mediaPathListSnapshot = mediaPathList.value
        mediaPathListSnapshot ?: return

        holder.bind(mediaPathListSnapshot[position])
    }

    override fun onViewRecycled(holder: Holder) {
        super.onViewRecycled(holder)
        holder.recycle()
    }

    inner class Holder(val binding: ListItemImageBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(path: String) {
            binding.viewModel = ItemListBitmapViewModel(dispatcher, coroutineScope, imageRecognizerChannel, path)
        }

        fun recycle() {
            binding.imageView.setImageDrawable(null)
            binding.viewModel?.recycle()
        }
    }

    class ItemListBitmapViewModel(
            private val dispatcher: CoroutineDispatcher,
            private val coroutineScope: CoroutineScope,
            private val channel: Channel<ImageRecognizer.Request>,
            private val path: String
    ) {
        val photoPrediction: ObservableField<Float> = ObservableField()
        val progressVisibility: ObservableField<Int> = ObservableField()

        private var thumbnailLoadingJob: Job? = null
        private var bitmap: Bitmap? = null

        val recognizerCallback: ImageRecognizer.Callback = object : ImageRecognizer.Callback {
            override fun onRecognize(confidence: Float) {
                val alpha = if (confidence > CONFIDENCE_THRESHOLD) {
                    ALPHA_IS_FOOD
                } else {
                    ALPHA_IS_NOT_FOOD
                }
                photoPrediction.set(alpha)
                progressVisibility.set(View.GONE)
            }
        }

        fun recycle() {
            thumbnailLoadingJob?.cancel()
            bitmap?.recycle()
        }

        @ExperimentalCoroutinesApi
        object ImageViewBindingAdapter {
            val options: BitmapFactory.Options = BitmapFactory.Options().apply {
                inSampleSize = 2
            }

            @BindingAdapter("path")
            @JvmStatic
            fun loadImage(imageView: ImageView, viewModel: ItemListBitmapViewModel) {
                viewModel.apply {
                    val drawable = when {
                        else -> {
                            startImageLoading(viewModel, imageView)
                            null
                        }
                    }
                    imageView.setImageDrawable(drawable)
                }
            }

            private fun startImageLoading(viewModel: ItemListBitmapViewModel, imageView: ImageView) {
                viewModel.apply {
                    bitmap?.let {
                        imageView.setImageBitmap(it)
                        return
                    }

                    progressVisibility.set(View.VISIBLE)

                    val myFlow = flow {
                        bitmap = BitmapFactory.decodeFile(path, options)
                        bitmap?.let {
                            emit(it)
                        }
                    }.flowOn(dispatcher)
                            .map { bitmap ->
                                imageView.setImageBitmap(bitmap)
                                bitmap
                            }.flowOn(Dispatchers.Main)
                            .map { bitmap ->
                                channel.send(ImageRecognizer.Request(bitmap, recognizerCallback))
                            }
                    thumbnailLoadingJob = myFlow.launchIn(coroutineScope)
                }
            }
        }

    }
}
