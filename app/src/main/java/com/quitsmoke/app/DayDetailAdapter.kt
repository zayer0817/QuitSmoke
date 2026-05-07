package com.quitsmoke.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.quitsmoke.app.data.SmokeRecord
import java.text.SimpleDateFormat
import java.util.*

/**
 * 每日详情列表适配器 - 展示每条记录的具体时间
 */
class DayDetailAdapter(
    private val records: List<SmokeRecord>
) : RecyclerView.Adapter<DayDetailAdapter.ViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIndex: TextView = view.findViewById(R.id.tv_detail_index)
        val tvTime: TextView = view.findViewById(R.id.tv_detail_time)
        val tvNote: TextView = view.findViewById(R.id.tv_detail_note)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_day_detail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // 从新到旧已排好序（DAO中ORDER BY timestamp DESC），第1条是最新的
        val record = records[position]
        holder.tvIndex.text = "第 ${records.size - position} 根"
        holder.tvTime.text = timeFormat.format(Date(record.timestamp))
        if (record.note.isNotBlank()) {
            holder.tvNote.text = record.note
            holder.tvNote.visibility = View.VISIBLE
        } else {
            holder.tvNote.visibility = View.GONE
        }
    }

    override fun getItemCount() = records.size
}
