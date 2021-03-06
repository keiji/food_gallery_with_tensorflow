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

import androidx.lifecycle.*

class MainViewModel(
        val mediaRepository: MediaRepository
) : ViewModel(), LifecycleObserver {

    private val _mediaPathList: MutableLiveData<ArrayList<String>> = MutableLiveData()
    val mediaPathList: LiveData<ArrayList<String>>
        get() = _mediaPathList

    fun loadMedias(limit: Int) {
        val mediaPathListSnapshot = mediaPathList.value ?: ArrayList()

        val resultArrayList = mediaRepository.getMediaPathList(mediaPathListSnapshot.size, limit)

        mediaPathListSnapshot.addAll(resultArrayList)
        _mediaPathList.value = mediaPathListSnapshot
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
    }
}