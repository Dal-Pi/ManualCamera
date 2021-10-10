package com.kania.manualcamera

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

// type helper used for the callback triggered once our view has been bound
typealias BindCallback<T> = (view: View, data: T, position: Int) -> Unit

//list adapter for generic types, intended user for small-medium list of data
class GenericListAdapter<T>(
    private val dataset: List<T>,
    private val itemLayoutId: Int? = null,
    private val itemViewFactory: (() -> View)? = null,
    private val onBindCallback: BindCallback<T>
) : RecyclerView.Adapter<GenericListAdapter.GenericListViewHolder>() {

    class GenericListViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = GenericListViewHolder(
        when {
            itemViewFactory != null -> itemViewFactory.invoke()
            itemLayoutId != null -> {
                LayoutInflater.from(parent.context).inflate(itemLayoutId, parent, false)
            }
            else -> {
                throw IllegalStateException(
                    "either the layout ID or the view factory need to be non-null"
                )
            }
        }
    )

    override fun onBindViewHolder(holder: GenericListViewHolder, position: Int) {
        if (position < 0 || position > dataset.size) return
        onBindCallback(holder.view, dataset[position], position)
    }

    override fun getItemCount() = dataset.size
}

