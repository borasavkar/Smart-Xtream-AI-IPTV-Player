package com.bybora.smartxtream.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bybora.smartxtream.R
import com.bybora.smartxtream.network.SeriesStream

interface OnSeriesClickListener {
    fun onSeriesClick(series: SeriesStream)
}

class SeriesAdapter(private val listener: OnSeriesClickListener) :
    ListAdapter<SeriesStream, SeriesAdapter.SeriesViewHolder>(SeriesDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeriesViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        return SeriesViewHolder(view)
    }

    override fun onBindViewHolder(holder: SeriesViewHolder, position: Int) {
        val series = getItem(position)
        holder.bind(series, listener)
    }

    class SeriesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.text_channel_name)
        private val icon: ImageView = itemView.findViewById(R.id.image_channel_icon)
        private val epgText: TextView = itemView.findViewById(R.id.text_epg_now)

        fun bind(series: SeriesStream, listener: OnSeriesClickListener) {
            // DÜZELTME: Sabit metin yerine çeviri kaynağı kullanıldı
            val context = itemView.context
            nameText.text = series.name ?: context.getString(R.string.untitled_series)

            epgText.visibility = View.GONE

            val imageUrl = series.cover ?: series.streamIcon
            Glide.with(itemView.context)
                .load(imageUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .into(icon)

            itemView.setOnClickListener {
                listener.onSeriesClick(series)
            }
        }
    }
}

class SeriesDiffCallback : DiffUtil.ItemCallback<SeriesStream>() {
    override fun areItemsTheSame(oldItem: SeriesStream, newItem: SeriesStream): Boolean {
        return oldItem.seriesId == newItem.seriesId
    }

    override fun areContentsTheSame(oldItem: SeriesStream, newItem: SeriesStream): Boolean {
        return oldItem == newItem
    }
}