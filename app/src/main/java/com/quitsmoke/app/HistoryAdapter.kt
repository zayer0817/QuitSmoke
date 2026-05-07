package com.quitsmoke.app

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.quitsmoke.app.data.DailyStat

/**
 * 历史记录列表适配器
 * 点击每行跳转到 DayDetailActivity 查看当天具体时间
 */
class HistoryAdapter(
    private val stats: List<DailyStat>
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tv_hist_date)
        val tvCount: TextView = view.findViewById(R.id.tv_hist_count)
        val tvLevel: TextView = view.findViewById(R.id.tv_hist_level)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val stat = stats[position]
        holder.tvDate.text = stat.dateStr
        holder.tvCount.text = "${stat.count} 根"

        val (level, color) = when {
            stat.count == 0 -> "无烟日 🎉" to 0xFF4CAF50.toInt()
            stat.count <= 5 -> "轻度 🟢" to 0xFF66BB6A.toInt()
            stat.count <= 10 -> "中度 🟡" to 0xFFFFC107.toInt()
            stat.count <= 20 -> "重度 🟠" to 0xFFFF9800.toInt()
            else -> "严重 🔴" to 0xFFF44336.toInt()
        }
        holder.tvLevel.text = level
        holder.tvLevel.setTextColor(color)

        // 点击跳转到当天详情页
        holder.itemView.setOnClickListener { view ->
            val intent = Intent(view.context, DayDetailActivity::class.java).apply {
                putExtra(DayDetailActivity.EXTRA_DATE, stat.dateStr)
            }
            view.context.startActivity(intent)
        }
    }

    override fun getItemCount() = stats.size
}
