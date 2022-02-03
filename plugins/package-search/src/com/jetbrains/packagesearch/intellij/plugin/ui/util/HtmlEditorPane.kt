package com.jetbrains.packagesearch.intellij.plugin.ui.util

import com.intellij.ide.ui.AntialiasingType
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.HyperlinkAdapter
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import javax.swing.text.DefaultCaret

@Suppress("LeakingThis")
internal open class HtmlEditorPane : JEditorPane() {

    init {
        @Suppress("MagicNumber") // UI code
        editorKit = HTMLEditorKitBuilder().withWordWrapViewFactory()
            .withGapsBetweenParagraphs().build().apply {
                //language=CSS
                styleSheet.addRule(
                    """
                      |ul {padding-left: ${8.scaled()}px;}
                      """.trimMargin()
                )

                //language=CSS
                styleSheet.addRule(
                    """
                          |a{color: ${JBUI.CurrentTheme.Link.Foreground.ENABLED.toCssHexColorString()};}
                          |a:link{color: ${JBUI.CurrentTheme.Link.Foreground.ENABLED.toCssHexColorString()};}
                          |a:visited{color: ${JBUI.CurrentTheme.Link.Foreground.VISITED.toCssHexColorString()};}
                          |a:active{color: ${JBUI.CurrentTheme.Link.Foreground.PRESSED.toCssHexColorString()};}
                          |a:hover{color: ${JBUI.CurrentTheme.Link.Foreground.HOVERED.toCssHexColorString()};}
                      """.trimMargin()
                )
            }

        highlighter = null
        isEditable = false
        isOpaque = false
        addHyperlinkListener(ProxyingHyperlinkListener(::onLinkClicked))
        margin = JBInsets.emptyInsets()
        GraphicsUtil.setAntialiasingType(this, AntialiasingType.getAAHintForSwingComponent())

        val caret = caret as DefaultCaret
        caret.updatePolicy = DefaultCaret.NEVER_UPDATE
    }

    protected fun clearBody() {
        setBody(emptyList())
    }

    fun setBody(chunks: Collection<HtmlChunk>) {
        text = if (chunks.isEmpty()) {
            ""
        } else {
            createBodyHtmlChunk()
                .children(*chunks.toTypedArray())
                .toString()
        }
    }

    fun setBodyText(@Nls text: String) {
        this.text = createBodyHtmlChunk()
            .addText(text)
            .toString()
    }

    private fun createBodyHtmlChunk(): HtmlChunk.Element {
        val style = buildString {
            append("color: ")
            append(foreground.toCssHexColorString())
            append("; font-size: ")
            append(font.size)
            append("pt;")
        }
        return HtmlChunk.body().style(style)
    }

    protected open fun onLinkClicked(anchor: String) {
        // No-op by default
    }

    final override fun addHyperlinkListener(listener: HyperlinkListener?) {
        super.addHyperlinkListener(listener)
    }

    final override fun removeHyperlinkListener(listener: HyperlinkListener?) {
        super.removeHyperlinkListener(listener)
    }

    private class ProxyingHyperlinkListener(private val callback: (anchor: String) -> Unit) : HyperlinkAdapter() {

        override fun hyperlinkActivated(e: HyperlinkEvent) {
            callback(e.description)
        }
    }
}
