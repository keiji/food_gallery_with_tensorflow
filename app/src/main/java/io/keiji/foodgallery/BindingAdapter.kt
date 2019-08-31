package io.keiji.foodgallery

import android.widget.ImageView
import androidx.databinding.BindingAdapter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.lang.ref.WeakReference

@UseExperimental(ExperimentalCoroutinesApi::class)
@BindingAdapter("path")
fun ImageView.loadImage(viewModel: MainAdapter.ItemListBitmapViewModel) {
    viewModel.startImageLoading(viewModel, WeakReference(this@loadImage))
}
