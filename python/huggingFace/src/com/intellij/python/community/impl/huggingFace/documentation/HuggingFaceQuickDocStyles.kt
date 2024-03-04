package com.intellij.python.community.impl.huggingFace.documentation

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.scale.JBUIScale
import org.intellij.lang.annotations.Language

/**
 * This object provides style adjustments for Hugging Face model cards.
 *
 * While the choice of parameters may seem unusual, the quick doc render framework that we use for HF cards
 * ignores certain styles (for example, it completely ignores the width attribute in an <img> tag).
 *
 * @See com.intellij.codeInsight.documentation.DocumentationHtmlUtil.getDocumentationPaneAdditionalCssRules
 * @See com.intellij.codeInsight.documentation.render.DocRenderer
 * @See com.intellij.lang.documentation.DocumentationMarkup
 */
@Suppress("SpellCheckingInspection")
object HuggingFaceQuickDocStyles {  // see PY-70541
  private fun scale(value: Int): Int = JBUIScale.scale(value)

  const val HF_PRE_TAG_CLASS = "word-break-pre-class"
  const val HF_P_TAG_CLASS = "increased-margin-p"
  const val CODE_DIV_CLASS = "code-fence-container"
  const val QUOTE_CLASS = "blockquote-inner"
  const val HF_CONTENT_CLASS = "hf-content"
  const val NBHP = "&#8209;"
  const val HAIR_SPACE = "&ensp;"
  val LINK_TOP_MARGIN = scale(6)

  @Language("CSS")
  @NlsSafe
  private val styleContent = listOf(
    "$HF_P_TAG_CLASS { margin-top: ${scale(4)}px; margin-bottom: ${scale(6)}px; }",

    ".$CODE_DIV_CLASS { padding-top: ${scale(4)}px; padding-bottom: ${scale(4)}px;  padding-left: ${scale(4)}px; " +
    "overflow-x: auto; background-color: rgba(0, 0, 0, 0.05); }",

    ".$CODE_DIV_CLASS code { padding-top: 0px; padding-bottom: 0px;  padding-left: 0px; " +
    "max-width: 100%; white-space: pre-wrap; word-wrap: break-word;  }",

    ".$HF_PRE_TAG_CLASS { white-space: pre-wrap; word-break: break-all; }",

    ".$QUOTE_CLASS { padding-left: ${scale(10)}px; }",

    ".$HF_CONTENT_CLASS { padding: ${scale(5)}px 0px ${scale(8)}px; max-width: 100% }",

    "blockquote { border-left: ${scale(4)}px solid #cccccc; }",

    "blockquote p { border-left: none; }",
  ).joinToString(separator = " ")

  fun styleChunk(): HtmlChunk = HtmlChunk.raw("<style>$styleContent</style>")
}
