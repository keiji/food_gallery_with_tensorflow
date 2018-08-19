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

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import io.reactivex.Single
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {

    companion object {
        val TAG = MainActivity::class.java.simpleName

        private val REQUEST_CODE_PERMISSION = 0x221
    }

    private var imageRecognizer: ImageRecognizer? = null
    private lateinit var adapter: Adapter

    private lateinit var recyclerView: RecyclerView

    @SuppressLint("Recycle")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recycler_view)
    }

    var cursor: Cursor? = null

    override fun onStart() {
        super.onStart()

        imageRecognizer = io.keiji.foodgallery.ImageRecognizer(assets)

        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(elements = Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_CODE_PERMISSION)
            return
        }

        initRecyclerView()
    }

    private fun initRecyclerView() {
        recyclerView.layoutManager = GridLayoutManager(this, 2)

        val projection = arrayOf(
                "_id",
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_ADDED
        )

        cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null, null,
                "%s DESC".format(MediaStore.Images.Media.DATE_ADDED))

        adapter = Adapter(cursor!!, LayoutInflater.from(this), imageRecognizer!!)
        recyclerView.adapter = adapter
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSION &&
                permissions.contains(Manifest.permission.READ_EXTERNAL_STORAGE) &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initRecyclerView()
        }
    }

    override fun onStop() {
        super.onStop()

        imageRecognizer?.stop()
        cursor?.close()
    }

    private class Adapter(
            cursor: Cursor,
            val inflater: LayoutInflater,
            val imageRecognizer: ImageRecognizer
    ) : AbsCursorRecycleAdapter<Adapter.ViewHolder>(cursor) {

        val pathIndex: Int = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

        val imageByteBuffer: ByteBuffer = ByteBuffer.allocate(ImageRecognizer.IMAGE_BYTES_LENGTH)

        val cacheBin: HashMap<String, Float> = HashMap()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = inflater.inflate(R.layout.list_item_image, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, cursor: Cursor) {
            val path = cursor.getString(pathIndex)

            holder.imageView.alpha = 0.0f
            holder.progress.visibility = View.VISIBLE

            holder.tag = path
            holder.disposable = LoadPhoto(path)
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .flatMap {
                        DisplayPhoto(it, holder)
                                .subscribeOn(AndroidSchedulers.mainThread())
                    }
                    .flatMap {
                        RecognizePhoto(it, path, cacheBin)
                                .subscribeOn(Schedulers.computation())
                    }
                    .subscribe({
                        if (path.equals(holder.tag)) {
                            val isFood = it > 0.5
                            holder.imageView.alpha = if (isFood) 1.0f else 0.2f
                            holder.progress.visibility = View.INVISIBLE
                        }
                    }, {})

        }

        override fun onViewRecycled(holder: ViewHolder) {
            super.onViewRecycled(holder)

            holder.disposable?.dispose()
            holder.imageView.setImageDrawable(null)
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView
            val progress: ProgressBar

            var disposable: Disposable? = null

            var tag: String? = null

            init {
                imageView = itemView.findViewById(R.id.image_view)
                progress = itemView.findViewById(R.id.progress)
            }
        }

        private inner class LoadPhoto(
                val path: String) : Single<Bitmap>() {

            override fun subscribeActual(observer: SingleObserver<in Bitmap>) {
                val options: BitmapFactory.Options = BitmapFactory.Options().apply {
                    inSampleSize = 2
                }
                BitmapFactory.decodeFile(path, options)?.let {
                    observer.onSuccess(it)
                }
            }
        }

        private inner class DisplayPhoto(
                val bitmap: Bitmap,
                val holder: ViewHolder) : Single<Bitmap>() {

            override fun subscribeActual(observer: SingleObserver<in Bitmap>) {
                holder.imageView.setImageBitmap(bitmap)

                observer.onSuccess(bitmap)
            }
        }

        private inner class RecognizePhoto(
                val bitmap: Bitmap,
                val path: String,
                val cacheBin: HashMap<String, Float>) : Single<Float>() {

            override fun subscribeActual(observer: SingleObserver<in Float>) {

                if (cacheBin.containsKey(path)) {
                    val result = cacheBin.get(path)
                    observer.onSuccess(result!!)
                    return
                }

                val scaledBitmap = ImageRecognizer.resizeToPreferSize(bitmap)

                synchronized(imageByteBuffer) {
                    scaledBitmap.copyPixelsToBuffer(imageByteBuffer)

                    // https://github.com/CyberAgent/android-gpuimage/issues/24
                    if (bitmap != scaledBitmap) {
                        scaledBitmap.recycle()
                    }

                    try {
                        val result = imageRecognizer.recognize(imageByteBuffer.array())
                        cacheBin.put(path, result)

                        observer.onSuccess(result)
                    } finally {
                        imageByteBuffer.clear()
                    }
                }
            }
        }
    }

}
