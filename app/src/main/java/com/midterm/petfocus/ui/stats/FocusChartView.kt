package com.midterm.petfocus.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.midterm.petfocus.R
import com.midterm.petfocus.logic.DayStat

/** Biểu đồ cột đơn giản: số phút tập trung mỗi ngày. Tự vẽ bằng Canvas, không cần thư viện ngoài. */
class FocusChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private fun c(id: Int) = ContextCompat.getColor(context, id)
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c(R.color.peach) }
    private val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c(R.color.peach_dark) }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c(R.color.chart_empty) }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c(R.color.text_secondary); textSize = 12 * density; textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c(R.color.text_primary); textSize = 11 * density; textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val rect = RectF()

    var days: List<DayStat> = emptyList()
        set(value) { field = value; contentDescription = value.joinToString { "${it.label}: ${it.minutes} phút" }; invalidate() }

    override fun onDraw(canvas: Canvas) {
        if (days.isEmpty()) return
        val labelH = 20 * density
        val valueH = 16 * density
        val chartTop = valueH
        val chartBottom = height - labelH
        val slot = width / days.size.toFloat()
        val barW = slot * 0.5f
        val max = (days.maxOf { it.minutes }).coerceAtLeast(1)
        val radius = 6 * density

        days.forEachIndexed { i, d ->
            val cx = slot * i + slot / 2
            val h = (chartBottom - chartTop) * d.minutes / max
            val minH = 4 * density
            rect.set(cx - barW / 2, chartBottom - maxOf(h, minH), cx + barW / 2, chartBottom)
            val paint = when {
                d.minutes == 0 -> emptyPaint
                i == days.lastIndex -> todayPaint
                else -> barPaint
            }
            canvas.drawRoundRect(rect, radius, radius, paint)
            if (d.minutes > 0) canvas.drawText("${d.minutes}", cx, rect.top - 4 * density, valuePaint)
            canvas.drawText(d.label, cx, height - 4 * density, labelPaint)
        }
    }
}
