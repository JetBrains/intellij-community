// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.documentation

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceEntityBasicApiData
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceURLProvider
import com.intellij.python.community.impl.huggingFace.documentation.HuggingFaceQuickDocStyles.LINK_TOP_MARGIN
import com.intellij.python.community.impl.huggingFace.service.PyHuggingFaceBundle

private val DOWNLOADS_ICON = HtmlChunk.tag("icon")
  .attr("src", "icons/download.svg")
private val LIKES_ICON = HtmlChunk.tag("icon")
  .attr(".src", "icons/like.svg")
private val LOGO_ICON = HtmlChunk.tag("icon")
  .attr(".src", "icons/logo.svg")

class HuggingFaceHtmlBuilder(
  private val project: Project,
  private val modelDataApiContent: HuggingFaceEntityBasicApiData,
  private val modelCardContent: String,
  private val entityKind: HuggingFaceEntityKind
) {
  @NlsSafe
  suspend fun build(): String {
    val headChunk = HtmlChunk.tag("head").child(HuggingFaceQuickDocStyles.styleChunk())
    val cardHeaderChunk = generateCardHeader(modelDataApiContent)
    @NlsSafe val convertedHtml = readAction { HuggingFaceMarkdownToHtmlConverter(project).convert(modelCardContent) }

    val wrappedBodyContent = HtmlChunk.div()
      .setClass(HuggingFaceQuickDocStyles.HF_CONTENT_CLASS)
      .child(HtmlChunk.raw(convertedHtml))

    val bodyChunk = HtmlChunk.tag("body")
      .children(
        cardHeaderChunk,
        wrappedBodyContent,
      )

    val htmlContent = HtmlChunk.tag("html").children(headChunk, bodyChunk)
    val htmlString = htmlContent.toString()
    return htmlString
  }

  private fun generateCardHeader(modelInfo: HuggingFaceEntityBasicApiData): HtmlChunk {
    val cardTitle = modelInfo.itemId.replace("-", HuggingFaceQuickDocStyles.NBHP)

    val modelNameWithIconRow = HtmlChunk.tag("h3")
      .children(
        HtmlChunk.raw(cardTitle),
        HtmlChunk.nbsp(2),
        LOGO_ICON
      )

    // modelPurpose chunk is not applicable for datasets
    val conditionalChunks = if (entityKind == HuggingFaceEntityKind.MODEL) {
      @NlsSafe val modelPurpose = modelInfo.pipelineTag
      listOf(HtmlChunk.text(modelPurpose), HtmlChunk.nbsp(2))
    } else {
      listOf(HtmlChunk.text(PyHuggingFaceBundle.message("python.hugging.face.dataset")), HtmlChunk.nbsp(2))
    }

    val modelInfoRow = DocumentationMarkup.GRAYED_ELEMENT
      .children(
        *conditionalChunks.toTypedArray(),
        HtmlChunk.text(PyHuggingFaceBundle.message("updated.0", modelInfo.humanReadableLastUpdated())),
        HtmlChunk.nbsp(2),

        DOWNLOADS_ICON,
        HtmlChunk.raw(HuggingFaceQuickDocStyles.HAIR_SPACE),
        HtmlChunk.text(modelInfo.humanReadableDownloads()),
        HtmlChunk.nbsp(2),

        LIKES_ICON,
        HtmlChunk.raw(HuggingFaceQuickDocStyles.HAIR_SPACE),
        HtmlChunk.raw(modelInfo.humanReadableLikes()),
        HtmlChunk.nbsp(),
      )

    val linkRow = HtmlChunk.tag("a")
      .attr("href", HuggingFaceURLProvider.getEntityCardLink(modelInfo.itemId, entityKind).toString())
      .children(
        HtmlChunk.text(PyHuggingFaceBundle.getMessage("open.on.hugging.face")),
        HtmlChunk.nbsp(),
        DocumentationMarkup.EXTERNAL_LINK_ICON
      )
      .wrapWith("p")
      .style("margin-top: ${LINK_TOP_MARGIN}px;") // compensate h3 tag bottom margin



      val headerContainer = HtmlChunk.div()
        .setClass(DocumentationMarkup.CLASS_DEFINITION)
        .children(
          modelNameWithIconRow,
          modelInfoRow,
          linkRow,
        )
    return headerContainer
  }
}
