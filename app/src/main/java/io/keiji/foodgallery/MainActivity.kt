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

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.keiji.foodgallery.databinding.ActivityMainBinding

const val DEFAULT_LIMIT = 50

class MainActivity : AppCompatActivity() {

    companion object {
        val TAG = MainActivity::class.java.simpleName

        private val REQUEST_CODE_PERMISSION = 0x221
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var layoutManager: GridLayoutManager

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(MediaRepository(contentResolver))
    }

    private val imageRecognizer by lazy { ImageRecognizer(assets) }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        private var prevIndex = -1

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            val visibleItemCount = recyclerView.childCount
            val totalItemCount = layoutManager.itemCount
            val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()

            val visibleLastItem = firstVisibleItem + visibleItemCount

            if (prevIndex == firstVisibleItem) {
                return
            }

            prevIndex = firstVisibleItem

            if (visibleLastItem >= (totalItemCount - 4)) {
                viewModel.loadMedias(DEFAULT_LIMIT)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycle.addObserver(viewModel)
        lifecycle.addObserver(imageRecognizer)

        val adapter = MainAdapter(this, this, viewModel.mediaPathList, imageRecognizer.channel)
        lifecycle.addObserver(adapter)

        layoutManager = GridLayoutManager(this, 2)
        binding = DataBindingUtil.setContentView<ActivityMainBinding>(
                this,
                R.layout.activity_main
        ).also {
            it.recyclerView.layoutManager = layoutManager
            it.recyclerView.adapter = adapter
            it.recyclerView.addOnScrollListener(scrollListener)
        }

        viewModel.mediaPathList.observe(this, Observer { mediaPathList ->
            binding.recyclerView.adapter?.let {
                adapter.notifyDataSetChanged()
            }
        })
    }

    override fun onStart() {
        super.onStart()

        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_CODE_PERMISSION)
            return
        }

        viewModel.loadMedias(DEFAULT_LIMIT)
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSION &&
                permissions.contains(Manifest.permission.READ_EXTERNAL_STORAGE) &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            viewModel.loadMedias(DEFAULT_LIMIT)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        lifecycle.removeObserver(viewModel)
    }
}
