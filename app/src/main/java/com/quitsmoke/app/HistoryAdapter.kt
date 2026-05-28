package com.quitsmoke.app

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.quitsmoke.app.data.DailyStat
import com.quitsmoke.app.databinding.ItemHistoryBinding

class HistoryAdapter(
    private val stats: List<DailyStat>
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

        val (level, colorRes) = when {
            stat.count == 0 -> R.string.level_none to R.color.green_good
            stat.count <= 5 -> R.string.level_light to R.color.green_light
            stat.count <= 10 -> R.string.level_moderate to R.color.yellow_warn
            stat.count <= 20 -> R.string.level_heavy to R.color.orange_alert
            else -> R.string.level_severe to R.color.red_danger
        }
        holder.binding.tvHistLevel.text = holder.itemView.context.getString(level)
        holder.binding.tvHistLevel.setTextColor(ContextCompat.getColor(holder.itemView.context, colorRes))

        holder.itemView.setOnClickListener { view ->
            val intent = Intent(view.context, DayDetailActivity::class.java).apply {
                putExtra(DayDetailActivity.EXTRA_DATE, stat.dateStr)
            }
            view.context.startActivity(intent)
        }
    }

    override fun getItemCount() = stats.size
}
