package com.midterm.petfocus.ui.art

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan
import androidx.appcompat.content.res.AppCompatResources
import com.midterm.petfocus.R

/**
 * Đổi emoji trong chữ thành icon vẽ tay của app (res/drawable/art_*.xml).
 *
 * Code và strings.xml vẫn viết emoji cho dễ đọc (vd "🪙 50 Xu"); khi hiển thị,
 * ArtTextView / ArtButton tự gọi [art] để thay bằng hình. Emoji nào chưa có hình
 * thì bị bỏ đi, để trong app không còn emoji của hệ thống.
 */
object ArtIcons {

    private val map: Map<String, Int> = mapOf(
        "🪙" to R.drawable.art_coin,
        "🛒" to R.drawable.art_bag,
        "🔥" to R.drawable.art_flame,
        "📚" to R.drawable.art_books,
        "✅" to R.drawable.art_check, "✔️" to R.drawable.art_check, "✔" to R.drawable.art_check,
        "⏱️" to R.drawable.art_timer, "⏱" to R.drawable.art_timer,
        "📊" to R.drawable.art_chart,
        "✨" to R.drawable.art_sparkle, "💪" to R.drawable.art_sparkle,
        "🍎" to R.drawable.art_fish, "🐟" to R.drawable.art_fish, "🍣" to R.drawable.art_fish,
        "💖" to R.drawable.art_heart, "💕" to R.drawable.art_heart, "❤️" to R.drawable.art_heart,
        "🤍" to R.drawable.art_heart_white,
        "💔" to R.drawable.art_heart_broken,
        "⚡" to R.drawable.art_bolt,
        "🛁" to R.drawable.art_drop,
        "⭐" to R.drawable.art_star, "🌟" to R.drawable.art_star,
        "🐱" to R.drawable.art_cat, "😺" to R.drawable.art_cat,
        "🥺" to R.drawable.art_cat_sad, "😿" to R.drawable.art_cat_sad, "😾" to R.drawable.art_cat_sad,
        "😪" to R.drawable.art_moon, "😴" to R.drawable.art_moon, "🌙" to R.drawable.art_moon,
        "🐾" to R.drawable.art_paw,
        "🔁" to R.drawable.art_repeat,
        "🔒" to R.drawable.art_lock,
        "🎉" to R.drawable.art_party,
        "🍪" to R.drawable.art_cookie,
        "🌿" to R.drawable.art_leaf,
        "🌱" to R.drawable.art_sprout,
        "🧴" to R.drawable.art_bottle,
        "☁️" to R.drawable.art_cloud, "☁" to R.drawable.art_cloud,
        "🌅" to R.drawable.art_sunset,
        "📋" to R.drawable.art_clipboard,
        "🏆" to R.drawable.art_trophy,
        "🧠" to R.drawable.art_bulb,
        "⚠️" to R.drawable.art_warning, "⚠" to R.drawable.art_warning,
    )

    /** Ghép dài trước để "⏱️" (có ký tự biến thể) được nhận trước "⏱". */
    private val keys = map.keys.sortedByDescending { it.length }

    /** Chữ có icon vẽ tay thay cho emoji. Gọi nhiều lần cũng không sao. */
    fun art(context: Context, text: CharSequence?): CharSequence? {
        if (text.isNullOrEmpty() || !hasEmoji(text)) return text
        if (text is Spanned && text.getSpans(0, text.length, ArtSpan::class.java).isNotEmpty()) return text

        val out = SpannableStringBuilder()
        var i = 0
        while (i < text.length) {
            val key = keys.firstOrNull { text.startsWith(it, i) }
            if (key != null) {
                val drawable = AppCompatResources.getDrawable(context, map.getValue(key))?.mutate()
                if (drawable != null) {
                    val start = out.length
                    out.append(PLACEHOLDER)
                    out.setSpan(ArtSpan(drawable), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                i += key.length
                continue
            }
            val cp = Character.codePointAt(text, i)
            if (!isEmoji(cp)) out.append(String(Character.toChars(cp)))
            i += Character.charCount(cp)
        }
        return out
    }

    /** Bỏ hết emoji, dùng cho nơi không hiển thị được hình (Toast, thông báo hệ thống). */
    fun plain(text: CharSequence): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            if (!isEmoji(cp)) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString().replace(Regex(" {2,}"), " ").trim()
    }

    private fun hasEmoji(text: CharSequence): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            if (isEmoji(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    /** Các khoảng mã Unicode chứa emoji và ký tự biến thể đi kèm. */
    private fun isEmoji(cp: Int) =
        cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2300..0x23FF ||
            cp in 0x2B00..0x2BFF || cp == 0xFE0F || cp == 0x200D

    private const val PLACEHOLDER = "￼"
}

/** Vẽ một icon to bằng cỡ chữ, căn giữa theo dòng chữ. */
class ArtSpan(private val drawable: Drawable) : ReplacementSpan() {

    private fun iconSize(paint: Paint) = (paint.textSize * 1.25f).toInt()

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val size = iconSize(paint)
        if (fm != null) {
            // Giữ chiều cao dòng như chữ bình thường, chỉ nới ra nếu icon cao hơn
            val pfm = paint.fontMetricsInt
            val textHeight = pfm.descent - pfm.ascent
            val extra = ((size - textHeight) / 2).coerceAtLeast(0)
            fm.ascent = pfm.ascent - extra
            fm.descent = pfm.descent + extra
            fm.top = minOf(pfm.top, fm.ascent)
            fm.bottom = maxOf(pfm.bottom, fm.descent)
        }
        return size + (paint.textSize * 0.08f).toInt()
    }

    override fun draw(
        canvas: Canvas, text: CharSequence?, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint
    ) {
        val size = iconSize(paint)
        val fm = paint.fontMetricsInt
        val centerY = y + (fm.ascent + fm.descent) / 2f
        canvas.save()
        canvas.translate(x, centerY - size / 2f)
        drawable.setBounds(0, 0, size, size)
        drawable.alpha = paint.alpha
        drawable.draw(canvas)
        canvas.restore()
    }
}
