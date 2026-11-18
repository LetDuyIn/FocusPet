package com.midterm.petfocus.ui.art

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView

/** TextView tự đổi emoji thành icon vẽ tay. Dùng thay cho TextView trong mọi layout. */
class ArtTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = android.R.attr.textViewStyle
) : MaterialTextView(context, attrs, defStyleAttr) {
    override fun setText(text: CharSequence?, type: BufferType?) {
        super.setText(ArtIcons.art(context, text), type)
    }
}

/** MaterialButton tự đổi emoji thành icon vẽ tay. */
class ArtButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle
) : MaterialButton(context, attrs, defStyleAttr) {
    override fun setText(text: CharSequence?, type: BufferType?) {
        super.setText(ArtIcons.art(context, text), type)
    }
}
