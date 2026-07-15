package com.quitsmoke.app.reminder

import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import com.quitsmoke.app.AppPreferences
import com.quitsmoke.app.R
import com.quitsmoke.app.data.SmokeRepository
import com.quitsmoke.app.databinding.ActivityReminderBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class ReminderActivity : androidx.appcompat.app.AppCompatActivity() {

    private lateinit var binding: ActivityReminderBinding
    private var period: String = "morning"
    private var missing: Int = 0
    private var actual: Int = 0
    private var expected: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Dialog-style: compact, centered
        setupDialogStyle()

        binding = ActivityReminderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        period = intent.getStringExtra(ReminderReceiver.EXTRA_PERIOD) ?: "morning"
        missing = intent.getIntExtra("missing", 0)
        actual = intent.getIntExtra("actual", 0)
        expected = intent.getIntExtra("expected", 0)

        setupUI()
        setupListeners()
    }

    private fun setupDialogStyle() {
        supportRequestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        window?.apply {
            setGravity(Gravity.CENTER)
            val params = attributes
            params.width = (resources.displayMetrics.widthPixels * 0.85).toInt()
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            attributes = params
            setBackgroundDrawableResource(android.R.color.transparent)
        }

        setFinishOnTouchOutside(true)
    }

    private fun setupUI() {
        val periodLabel = AppPreferences.getPeriodLabel(period)

        if (period == "end_of_day") {
            binding.tvTitle.text = "今日补录"
            binding.tvDescription.text = "今天共记录了 $actual 根\n平时大约 $expected 根\n还差 $missing 根，要补上吗？"
        } else {
            binding.tvTitle.text = "${periodLabel}补录"
            binding.tvDescription.text = "${periodLabel}只记录了 $actual 根\n平时大约抽 $expected 根\n还差 $missing 根，是忘记录了吗？"
        }

        binding.btnFill.text = "补上 $missing 根"
        binding.btnSkip.text = "今天抽得少"
        binding.btnLater.text = "稍后提醒"
    }

    private fun setupListeners() {
        binding.btnFill.setOnClickListener {
            fillMissing()
        }

        binding.btnSkip.setOnClickListener {
            AppPreferences.markPeriodSkippedToday(this, period)
            finish()
        }

        binding.btnLater.setOnClickListener {
            // 30分钟后再次检查
            ReminderReceiver.scheduleReminderDelayed(this, period, 30)
            finish()
        }
    }

    private fun fillMissing() {
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            binding.btnFill.isEnabled = false
            binding.btnFill.text = "记录中..."

            val count = withContext(Dispatchers.IO) {
                val repo = SmokeRepository.getInstance(this@ReminderActivity)
                val calendar = Calendar.getInstance()
                var inserted = 0
                // Add records spread across the period's time range
                val (startHour, endHour) = AppPreferences.getPeriodHourRange(period)
                val hourSpan = if (endHour > startHour) endHour - startHour else 1
                val interval = (hourSpan * 60) / (missing + 1)

                for (i in 0 until missing) {
                    calendar.set(Calendar.HOUR_OF_DAY, startHour)
                    calendar.set(Calendar.MINUTE, (i + 1) * interval)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    repo.recordSmokeAt(calendar.timeInMillis)
                    inserted++
                }
                inserted
            }

            // Mark as done for today
            AppPreferences.markPeriodSkippedToday(this@ReminderActivity, period)

            binding.tvDescription.text = "已补上 $count 根！"
            binding.btnFill.text = "完成"
            binding.btnFill.isEnabled = true
            binding.btnSkip.visibility = android.view.View.GONE
            binding.btnLater.visibility = android.view.View.GONE

            binding.btnFill.setOnClickListener { finish() }

            // Auto close after 2 seconds
            binding.root.postDelayed({ finish() }, 2000)
        }
    }
}
