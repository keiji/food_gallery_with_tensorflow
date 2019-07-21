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

import android.content.ContentResolver
import android.provider.MediaStore

private const val ORDER_BY = MediaStore.Images.Media.DATE_ADDED

class MediaRepository(val contentResolver: ContentResolver) {

    private val projection = arrayOf(
            "_id",
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED
    )

    fun getMediaPathList(offset: Int, limit: Int): ArrayList<String> {
        val cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null, null,
                "${ORDER_BY} DESC LIMIT ${limit} OFFSET ${offset} ")

        val resultArrayList = ArrayList<String>()

        cursor ?: return resultArrayList

        val pathIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

        cursor.moveToFirst()
        do {
            resultArrayList.add(cursor.getString(pathIndex))
        } while (cursor.moveToNext())

        cursor.close()

        return resultArrayList
    }
}