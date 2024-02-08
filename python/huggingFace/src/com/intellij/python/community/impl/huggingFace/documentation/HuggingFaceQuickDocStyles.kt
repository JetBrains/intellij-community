package com.intellij.python.community.impl.huggingFace.documentation

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk

/**
 * This object provides style adjustments for Hugging Face model cards.
 *
 * While the choice of parameters may seem unusual, the quick doc render framework that we use for HF cards
 * ignores certain styles (for example, it completely ignores the width attribute in an <img> tag).
 *
 * @See com.intellij.codeInsight.documentation.DocumentationHtmlUtil.getDocumentationPaneDefaultCssRules
 * @See com.intellij.codeInsight.documentation.render.DocRenderer
 */
object HuggingFaceQuickDocStyles {
  const val HF_PRE_TAG_CLASS = "word-break-pre-class"
  const val HF_P_TAG_CLASS = "increased-margin-p"
  const val CODE_DIV_CLASS = "code-fence-container"
  const val QUOTE_CLASS = "blockquote-inner"
  const val CARD_HEADER_MARGIN = "model-name-with-icon-row"
  const val LINK_ROW_STYLE = "link-row-custom"
  const val HF_GRAYED_CLASS = "grayed-hf"
  const val NBHP = "&#8209;"

  @NlsSafe
  private val styleContent = listOf(
    ".$CARD_HEADER_MARGIN { margin-bottom: 4px }",
    "$HF_P_TAG_CLASS { margin-top: 4px; margin-bottom: 6px; }",
    ".$CODE_DIV_CLASS { padding-top: 4px; padding-bottom: 4px;  padding-left: 4px; " +
    "overflow-x: auto; background-color: rgba(0, 0, 0, 0.05); }",
    ".$CODE_DIV_CLASS code { padding-top: 0px; padding-bottom: 0px;  padding-left: 0px; " +
    "max-width: 100%; white-space: pre-wrap; word-wrap: break-word;  }",
    ".$HF_PRE_TAG_CLASS { white-space: pre-wrap; word-break: break-all; }",
    ".$LINK_ROW_STYLE { margin-top: 0px; }",
    ".$QUOTE_CLASS { padding-left: 10px; }",
    ".$HF_GRAYED_CLASS { color: #909090; display: inline; white-space: nowrap; word-break: keep-all;}",
    "blockquote { border-left: 4px solid #cccccc; }",
    "blockquote p { border-left: none; }",
    "h1 { white-space: nowrap; word-break: keep-all; }"
  ).joinToString(separator = " ")

  fun styleChunk(): HtmlChunk = HtmlChunk.raw("<style>$styleContent</style>")
}
