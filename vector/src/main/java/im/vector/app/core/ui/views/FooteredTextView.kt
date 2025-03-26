package im.vector.app.core.ui.views

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class FooteredTextView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
): AppCompatTextView(context, attrs, defStyleAttr), AbstractFooteredTextView {

    override val footerState: AbstractFooteredTextView.FooterState = AbstractFooteredTextView.FooterState()
    override fun getAppCompatTextView(): AppCompatTextView = this
    override fun setMeasuredDimensionExposed(measuredWidth: Int, measuredHeight: Int) = setMeasuredDimension(measuredWidth, measuredHeight)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // First, let super measure the content for our normal TextView use
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val updatedMeasures = updateDimensionsWithFooter(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(updatedMeasures.first, updatedMeasures.second)
    }

    override fun onDraw(canvas: Canvas) {
        updateFooterOnPreDraw(canvas)

        super.onDraw(canvas)
    }
}
