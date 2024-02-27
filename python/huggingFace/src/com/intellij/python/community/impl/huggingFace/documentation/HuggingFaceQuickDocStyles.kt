package com.intellij.python.community.impl.huggingFace.documentation

import com.intellij.codeInsight.documentation.DocumentationHtmlUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import org.intellij.lang.annotations.Language

/**
 * This object provides style adjustments for Hugging Face model cards.
 *
 * While the choice of parameters may seem unusual, the quick doc render framework that we use for HF cards
 * ignores certain styles (for example, it completely ignores the width attribute in an <img> tag).
 *
 * @See com.intellij.codeInsight.documentation.DocumentationHtmlUtil.getDocumentationPaneDefaultCssRules
 * @See com.intellij.codeInsight.documentation.render.DocRenderer
 * @See com.intellij.lang.documentation.DocumentationMarkup
 */
@Suppress("SpellCheckingInspection")
object HuggingFaceQuickDocStyles {  // see PY-70541
  const val HF_PRE_TAG_CLASS = "word-break-pre-class"
  const val HF_P_TAG_CLASS = "increased-margin-p"
  const val CODE_DIV_CLASS = "code-fence-container"
  const val QUOTE_CLASS = "blockquote-inner"
  const val HF_GRAYED_CLASS = "grayed-hf"
  const val HF_CONTENT_CLASS = "hf-content"
  const val NBHP = "&#8209;"
  const val HAIR_SPACE = "&ensp;"

  private val spacing = DocumentationHtmlUtil.contentSpacing

  @Language("CSS")
  @NlsSafe
  private val styleContent = listOf(
    "$HF_P_TAG_CLASS { margin-top: 4px; margin-bottom: 6px; }",
    ".$CODE_DIV_CLASS { padding-top: 4px; padding-bottom: 4px;  padding-left: 4px; " +
    "overflow-x: auto; background-color: rgba(0, 0, 0, 0.05); }",
    ".$CODE_DIV_CLASS code { padding-top: 0px; padding-bottom: 0px;  padding-left: 0px; " +
    "max-width: 100%; white-space: pre-wrap; word-wrap: break-word;  }",
    ".$HF_PRE_TAG_CLASS { white-space: pre-wrap; word-break: break-all; }",
    ".$QUOTE_CLASS { padding-left: 10px; }",
    ".$HF_GRAYED_CLASS { color: #909090; display: inline; white-space: nowrap; " +
    "word-break: keep-all; padding-bottom: ${spacing}px; padding-top: -4px; }",
    ".$HF_CONTENT_CLASS { padding: 5px 0px 8px; max-width: 100% }",
    "blockquote { border-left: 4px solid #cccccc; }",
    "blockquote p { border-left: none; }",
    ).joinToString(separator = " ")

  fun styleChunk(): HtmlChunk = HtmlChunk.raw("<style>$styleContent</style>")
}
