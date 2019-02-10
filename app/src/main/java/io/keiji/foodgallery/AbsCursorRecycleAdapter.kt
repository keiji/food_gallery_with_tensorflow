package io.keiji.foodgallery

import android.database.DataSetObserver
import android.database.ContentObserver
import android.database.Cursor
import android.os.Handler
import android.provider.MediaStore
import androidx.recyclerview.widget.RecyclerView

/**
 * Wasabeef - RecyclerViewでCursorを使う.
 *
 * @See https://wasabeef.jp/android-recyclerview-cursor/
 */
abstract class AbsCursorRecycleAdapter<VH : RecyclerView.ViewHolder>(
        protected var cursor: Cursor) : RecyclerView.Adapter<VH>() {
    protected var dataValid = true
    protected var rowIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)

    protected val changeObserver: ChangeObserver = ChangeObserver()
    protected val dataSetObserver: DataSetObserver = CursorDataSetObserver()

    init {
        cursor.registerContentObserver(changeObserver)
        cursor.registerDataSetObserver(dataSetObserver)
        setHasStableIds(true)
    }

    override fun getItemCount(): Int {
        return if (dataValid) cursor.getCount() else 0
    }

    override fun getItemId(position: Int): Long {
        if (!dataValid) {
            return 0
        }

        return if (cursor.moveToPosition(position)) cursor.getLong(rowIdColumn) else 0
    }

    abstract fun onBindViewHolder(holder: VH, cursor: Cursor)

    override fun onBindViewHolder(holder: VH, position: Int) {
        if (!dataValid) {
            throw IllegalStateException("This should only be called when the cursor is valid")
        }
        if (!cursor.moveToPosition(position)) {
            throw IllegalStateException("Couldn't move cursor to position $position")
        }
        onBindViewHolder(holder, cursor)
    }

    fun changeCursor(cursor: Cursor) {
        swapCursor(cursor)?.close()
    }

    private fun swapCursor(newCursor: Cursor): Cursor? {
        if (newCursor === cursor) {
            return null
        }

        val oldCursor = cursor
        oldCursor.unregisterContentObserver(changeObserver)
        oldCursor.unregisterDataSetObserver(dataSetObserver)

        cursor = newCursor
        newCursor.registerContentObserver(changeObserver)
        newCursor.registerDataSetObserver(dataSetObserver)

        rowIdColumn = newCursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        dataValid = true

        notifyDataSetChanged()

        return oldCursor
    }

    protected fun onChanged() {
    }

    inner class ChangeObserver : ContentObserver(Handler()) {

        override fun deliverSelfNotifications(): Boolean {
            return true
        }

        override fun onChange(selfChange: Boolean) {
            onChanged()
        }
    }

    private inner class CursorDataSetObserver : DataSetObserver() {

        override fun onChanged() {
            dataValid = true
            notifyDataSetChanged()
        }

        override fun onInvalidated() {
            dataValid = false
            notifyDataSetChanged()
        }
    }
}