package com.quitsmoke.app

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.quitsmoke.app.data.DailyStat
import com.quitsmoke.app.databinding.ItemHistoryBinding

class HistoryAdapter(
    private val stats: List<DailyStat>,
    private val themeColor: Int
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val stat = stats[position]
        holder.binding.tvHistDate.text = stat.dateStr
        holder.binding.tvHistCount.text = holder.itemView.context.getString(R.string.count_cigarettes, stat.count)

        val level = when {
            stat.count == 0 -> R.string.level_none
            stat.count <= 3 -> R.string.level_light
            stat.count <= 6 -> R.string.level_moderate
            stat.count <= 10 -> R.string.level_heavy
            else -> R.string.level_severe
        }
        holder.binding.tvHistLevel.text = holder.itemView.context.getString(level)
        holder.binding.tvHistLevel.setTextColor(themeColor)

        holder.itemView.setOnClickListener { view ->
            val intent = Intent(view.context, DayDetailActivity::class.java).apply {
                putExtra(DayDetailActivity.EXTRA_DATE, stat.dateStr)
            }
            view.context.startActivity(intent)
        }
    }

    override fun getItemCount() = stats.size
}
