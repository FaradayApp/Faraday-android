/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

/*
 * This file renders the formatted_body of an event to a formatted Android Spannable.
 * The core of this work is done with Markwon, a general-purpose Markdown+HTML formatter.
 * Since formatted_body is HTML only, Markwon is configured to only handle HTML, not Markdown.
 * The EventHtmlRenderer class is next used in the method buildFormattedTextItem
 * in the file MessageItemFactory.kt.
 * Effectively, this is used in the chat messages view and the room list message previews.
 */

package im.vector.app.features.html

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StrikethroughSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.widget.TextView
import androidx.core.text.toSpannable
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.resource.bitmap.TransformationUtils
import com.bumptech.glide.request.target.Target
import im.vector.app.R
import im.vector.app.core.di.ActiveSessionHolder
import im.vector.app.core.resources.BuildMeta
import im.vector.app.core.resources.ColorProvider
import im.vector.app.core.utils.DimensionConverter
import im.vector.app.features.settings.VectorPreferences
import im.vector.app.features.themes.ThemeUtils
import io.element.android.wysiwyg.view.spans.InlineCodeSpan
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonPlugin
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.PrecomputedFutureTextSetterCompat
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.core.spans.EmphasisSpan
import io.noties.markwon.core.spans.StrongEmphasisSpan
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.latex.JLatexMathTheme
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.glide.GlideImagesPlugin
import io.noties.markwon.inlineparser.EntityInlineProcessor
import io.noties.markwon.inlineparser.HtmlInlineProcessor
import io.noties.markwon.inlineparser.MarkwonInlineParser
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import me.gujun.android.span.style.CustomTypefaceSpan
import org.commonmark.node.Emphasis
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.matrix.android.sdk.api.MatrixUrls.isMxcUrl
import org.matrix.android.sdk.api.extensions.tryOrNull
import org.matrix.android.sdk.api.util.ContentUtils.extractUsefulTextFromHtmlReply
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventHtmlRenderer @Inject constructor(
        private val htmlConfigure: MatrixHtmlPluginConfigure,
        private val context: Context,
        private val dimensionConverter: DimensionConverter,
        private val vectorPreferences: VectorPreferences,
        private val activeSessionHolder: ActiveSessionHolder,
        private val buildMeta: BuildMeta,
) {

    companion object {
        private const val IMG_HEIGHT_ATTR = "height"
        private const val IMG_WIDTH_ATTR = "width"
        private val IMG_HEIGHT_REGEX = IMG_HEIGHT_ATTR.toAttrRegex()
        private val IMG_WIDTH_REGEX = IMG_WIDTH_ATTR.toAttrRegex()

        private fun String.toAttrRegex(): Regex {
            return Regex("""\s+$this="([^"]*)"""")
        }
    }

    private fun resolveCodeBlockBackground() =
            ThemeUtils.getColor(context, im.vector.lib.ui.styles.R.attr.code_block_bg_color)
    private fun resolveQuoteBarColor() =
            ThemeUtils.getColor(context, im.vector.lib.ui.styles.R.attr.quote_bar_color)

    private var codeBlockBackground: Int = resolveCodeBlockBackground()
    private var quoteBarColor: Int = resolveQuoteBarColor()

    interface PostProcessor {
        fun afterRender(renderedText: Spannable)
    }

    private fun String.removeHeightWidthAttrs(): String {
        return replace(IMG_HEIGHT_REGEX, "")
                .replace(IMG_WIDTH_REGEX, "")
    }

    private fun String.scalePx(regex: Regex, attr: String): Pair<String, Boolean> {
        // To avoid searching the same regex multiple times, return if we actually found this attr
        var foundAttr = false
        val result = tryOrNull {
            regex.find(this)?.let {
                foundAttr = true
                val pixelSize = dimensionConverter.dpToPx(it.groupValues[1].toInt())
                this.replaceRange(it.range, """ $attr="$pixelSize"""")
            }
        } ?: this
        return Pair(result, foundAttr)
    }

    private fun String.scaleImageHeightAndWidth(): String {
        return tryOrNull { this.replace(Regex("""<img(\s+[^>]*)>""")) { matchResult ->
            var result = Pair(matchResult.groupValues[1], false)
            var foundDimension = false
            for (dimension in listOf(Pair(IMG_WIDTH_ATTR, IMG_WIDTH_REGEX), Pair(IMG_HEIGHT_ATTR, IMG_HEIGHT_REGEX))) {
                result = result.first.scalePx(dimension.second, dimension.first)
                foundDimension = foundDimension || result.second
            }
            if (foundDimension) {
                matchResult.groupValues[0].replace(matchResult.groupValues[1], result.first)
            } else {
                // Fallback height to ensure sane measures
                //matchResult.groupValues[0].replace(matchResult.groupValues[1], """ height="3em" ${matchResult.groupValues[1]}""")
                matchResult.groupValues[0]
            }
        } } ?: this
    }

    // https://github.com/bumptech/glide/issues/2391#issuecomment-336798418
    class MaxSizeTransform(private val maxWidth: Int, private val maxHeight: Int) : BitmapTransformation() {
        private val ID = "MaxSizeTransform"
        private val ID_BYTES = ID.toByteArray()

        override fun updateDiskCacheKey(messageDigest: MessageDigest) {
            messageDigest.update(ID_BYTES)
        }

        override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
            if (toTransform.height <= maxHeight && toTransform.width <= maxWidth) {
                return toTransform;
            }
            return if (toTransform.height / maxHeight > toTransform.width / maxWidth) {
                // Scale to maxHeight
                TransformationUtils.centerCrop(
                        pool, toTransform,
                        toTransform.width * maxHeight / toTransform.height, maxHeight
                )
            } else {
                // Scale to maxWidth
                TransformationUtils.centerCrop(
                        pool, toTransform,
                        maxWidth, toTransform.height * maxWidth / toTransform.width
                )
            }
        }
    }

    private val glidePlugin = GlideImagesPlugin.create(object : GlideImagesPlugin.GlideStore {
        override fun load(drawable: AsyncDrawable): RequestBuilder<Drawable> {
            val url = drawable.destination
            if (url.isMxcUrl()) {
                val contentUrlResolver = activeSessionHolder.getActiveSession().contentUrlResolver()
                val imageUrl = contentUrlResolver.resolveFullSize(url)
                // Set max size to avoid crashes for huge pictures, and also ensure sane measures while showing on screen
                return Glide.with(context).load(imageUrl).transform(MaxSizeTransform(1000, 500))
            }
            // We don't want to support other url schemes here, so just return a request for null
            return Glide.with(context).load(null as String?)
        }

        override fun cancel(target: Target<*>) {
            Glide.with(context).clear(target)
        }
    })

    private val latexPlugins = listOf(
            object : AbstractMarkwonPlugin() {
                override fun processMarkdown(markdown: String): String {
                    return markdown
                            .replace(Regex("""<span\s+data-mx-maths="([^"]*)">.*?</span>""")) { matchResult ->
                                "$$" + matchResult.groupValues[1] + "$$"
                            }
                            .replace(Regex("""<div\s+data-mx-maths="([^"]*)">.*?</div>""")) { matchResult ->
                                "\n$$\n" + matchResult.groupValues[1] + "\n$$\n"
                            }
                }
            },
            JLatexMathPlugin.create(44F) { builder ->
                builder.inlinesEnabled(true)
                builder.theme().inlinePadding(JLatexMathTheme.Padding.symmetric(24, 8))
            }
    )

    private val markwonInlineParserPlugin =
            MarkwonInlineParserPlugin.create(
                    /* Configuring the Markwon inline formatting processor.
                     * Default settings are all Markdown features. Turn those off, only using the
                     * inline HTML processor and HTML entities processor.
                     */
                    MarkwonInlineParser.factoryBuilderNoDefaults()
                            .addInlineProcessor(HtmlInlineProcessor()) // use inline HTML processor
                            .addInlineProcessor(EntityInlineProcessor()) // use HTML entities processor
            )

    private val italicPlugin = object : AbstractMarkwonPlugin() {
        override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
            builder.setFactory(
                    Emphasis::class.java
            ) { _, _ -> CustomTypefaceSpan(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)) }
        }

        override fun configureParser(builder: Parser.Builder) {
            /* Configuring the Markwon block formatting processor.
             * Default settings are all Markdown blocks. Turn those off.
             */
            builder.enabledBlockTypes(emptySet())
        }
    }

    private val cleanUpIntermediateCodePlugin = object : AbstractMarkwonPlugin() {
        override fun afterSetText(textView: TextView) {
            super.afterSetText(textView)

            // Remove any intermediate spans
            val text = textView.text.toSpannable()
            text.getSpans(0, text.length, IntermediateCodeSpan::class.java)
                    .forEach { span ->
                        text.removeSpan(span)
                    }
        }
    }

    /**
     * Workaround for https://github.com/noties/Markwon/issues/423
     */
    private val removeLeadingNewlineForInlineElement = object : AbstractMarkwonPlugin() {
        override fun afterSetText(textView: TextView) {
            super.afterSetText(textView)

            val text = SpannableStringBuilder(textView.text.toSpannable())
            val length = textView.length()
            val spans = arrayOf(
                    InlineCodeSpan::class.java,
                    EmphasisSpan::class.java,
                    CustomTypefaceSpan::class.java,
                    StrongEmphasisSpan::class.java,
                    UnderlineSpan::class.java,
                    URLSpan::class.java,
                    StrikethroughSpan::class.java
            ).map { text.getSpans(0, length, it) }
                    .toTypedArray()
                    .plus(text.getSpans(0, length, HtmlCodeSpan::class.java).filter { !it.isBlock }.toTypedArray())
                    .flatten()

            if (spans.isEmpty()) return

            spans.forEach { span ->
                val start = text.getSpanStart(span)
                if (start < 0) {
                    return@forEach
                }
                if (text[start] == '\n') {
                    text.replace(start, start + 1, "")
                }
            }

            textView.text = text
        }
    }

    private val themePlugin = object: AbstractMarkwonPlugin() {
        override fun configureTheme(builder: MarkwonTheme.Builder) {
            super.configureTheme(builder)
            builder.codeBlockBackgroundColor(codeBlockBackground)
                    .codeBackgroundColor(codeBlockBackground)
                    .blockQuoteColor(quoteBarColor)
                    .blockQuoteWidth(dimensionConverter.dpToPx(2))
                    .blockMargin(dimensionConverter.dpToPx(8))
        }
    }

    private val removeMxReplyFallbackPlugin = object: AbstractMarkwonPlugin() {
        override fun processMarkdown(markdown: String): String {
            return extractUsefulTextFromHtmlReply(markdown)
        }
    }

    // Overwrite height for data-mx-emoticon, to ensure emoji-like height
    private val adjustEmoticonHeight = object : AbstractMarkwonPlugin() {
        override fun processMarkdown(markdown: String): String {
            return markdown
                    .replace(Regex("""<img\s+([^>]*)data-mx-emoticon([^>]*)>""")) { matchResult ->
                        """<img height="1.2em" """ + matchResult.groupValues[1].removeHeightWidthAttrs() +
                                " data-mx-emoticon" + matchResult.groupValues[2].removeHeightWidthAttrs() + ">"
                    }
                    // Note: doesn't scale previously set mx-emoticon height, since "1.2em" is no integer
                    // (which strictly shouldn't be allowed, but we're hacking our way around the library already either way)
                    .scaleImageHeightAndWidth()
        }
    }


    private fun buildMarkwon() = Markwon.builder(context)
            .usePlugin(removeMxReplyFallbackPlugin)
            .usePlugin(HtmlRootTagPlugin())
            .usePlugin(HtmlPlugin.create(htmlConfigure))
            .usePlugin(themePlugin)
            .usePlugin(adjustEmoticonHeight)
            .usePlugin(removeLeadingNewlineForInlineElement)
            .usePlugin(DetailsTagPostProcessor(this))
            .usePlugin(glidePlugin)
            .apply {
                if (vectorPreferences.latexMathsIsEnabled()) {
                    // If latex maths is enabled in app preferences, reformat it so Markwon recognises it
                    // It needs to be in this specific format: https://noties.io/Markwon/docs/v4/ext-latex
                    latexPlugins.forEach(::usePlugin)
                }
            }
            .usePlugin(markwonInlineParserPlugin)
            .usePlugin(italicPlugin)
            .usePlugin(cleanUpIntermediateCodePlugin)
            .textSetter(PrecomputedFutureTextSetterCompat.create())
            .build()

    private var markwon: Markwon = buildMarkwon()
    get() {
        val newCodeBlockBackground = resolveCodeBlockBackground()
        val newQuoteBarColor = resolveQuoteBarColor()
        var changed = false
        if (codeBlockBackground != newCodeBlockBackground) {
            codeBlockBackground = newCodeBlockBackground
            changed = true
        }
        if (quoteBarColor != newQuoteBarColor) {
            quoteBarColor = newQuoteBarColor
            changed = true
        }
        if (changed) {
            field = buildMarkwon()
        }
        return field
    }

    val plugins: List<MarkwonPlugin> = markwon.plugins

    fun invalidateColors() {
        markwon = buildMarkwon()
    }

    fun parse(text: String): Node {
        return markwon.parse(text)
    }

    /**
     * @param text the text you want to render
     * @param postProcessors an optional array of post processor to add any span if needed
     */
    fun render(text: String, vararg postProcessors: PostProcessor): CharSequence {
        return try {
            val parsed = markwon.parse(text)
            renderAndProcess(parsed, postProcessors)
        } catch (failure: Throwable) {
            Timber.v(failure, "Fail to render text ${text.redactIfNotDebug()} to html")
            text
        }
    }

    // Do not leak message content
    fun String.redactIfNotDebug(): String {
        return if (buildMeta.isInternalBuild) {
            this
        } else {
            "(REDACTED)"
        }
    }

    /**
     * @param node the node you want to render
     * @param postProcessors an optional array of post processor to add any span if needed
     */
    fun render(node: Node, vararg postProcessors: PostProcessor): CharSequence? {
        return try {
            renderAndProcess(node, postProcessors)
        } catch (failure: Throwable) {
            Timber.v(failure, "Fail to render node ${node.toString().redactIfNotDebug()} to html")
            return null
        }
    }

    private fun renderAndProcess(node: Node, postProcessors: Array<out PostProcessor>): CharSequence {
        val renderedText = markwon.render(node).toSpannable()
        postProcessors.forEach {
            it.afterRender(renderedText)
        }
        return renderedText
    }
}

class MatrixHtmlPluginConfigure @Inject constructor(
        private val colorProvider: ColorProvider,
        private val resources: Resources,
        private val vectorPreferences: VectorPreferences,
        private val dimensionConverter: DimensionConverter,
) : HtmlPlugin.HtmlConfigure {

    override fun configureHtml(plugin: HtmlPlugin) {
        plugin
                .addHandler(DetailsTagHandler())
                .addHandler(ListHandlerWithInitialStart())
                .addHandler(FontTagHandler())
                .addHandler(ParagraphHandler(DimensionConverter(resources)))
                // Note: only for fallback replies, which we should have removed by now
                .addHandler(MxReplyTagHandler())
                .addHandler(CodePostProcessorTagHandler(vectorPreferences, dimensionConverter))
                .addHandler(CodePreTagHandler())
                .addHandler(CodeTagHandler())
                .addHandler(SpanHandler(colorProvider))
    }
}
