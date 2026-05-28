package com.quitsmoke.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.quitsmoke.app.data.SmokeRecord
import com.quitsmoke.app.databinding.ItemDayDetailBinding
import java.text.SimpleDateFormat
import java.util.*

class DayDetailAdapter(
    private val records: List<SmokeRecord>
) : RecyclerView.Adapter<DayDetailAdapter.ViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    class ViewHolder(val binding: ItemDayDetailBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDayDetailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        holder.binding.tvDetailIndex.text = holder.itemView.context.getString(R.string.detail_index_format, records.size - position)
        holder.binding.tvDetailTime.text = timeFormat.format(Date(record.timestamp))
        if (record.note.isNotBlank()) {
            holder.binding.tvDetailNote.text = record.note
            holder.binding.tvDetailNote.visibility = View.VISIBLE
        } else {
            holder.binding.tvDetailNote.visibility = View.GONE
        }
    }

    override fun getItemCount() = records.size
}
