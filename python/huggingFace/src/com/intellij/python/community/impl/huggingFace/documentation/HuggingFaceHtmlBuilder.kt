// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.documentation

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.python.community.impl.huggingFace.HuggingFaceConstants
import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceEntityBasicApiData
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceURLProvider
import com.intellij.python.community.impl.huggingFace.service.PyHuggingFaceBundle

private val DOWNLOADS_ICON = HtmlChunk.tag("icon")
  .attr("src", "com.intellij.python.community.impl.huggingFace.PythonCommunityImplHuggingFaceIcons.Download")
private val LIKES_ICON = HtmlChunk.tag("icon")
  .attr(".src", "com.intellij.python.community.impl.huggingFace.PythonCommunityImplHuggingFaceIcons.Like")


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

    val invisibleSpan = createInvisibleSpan(convertedHtml)

    val bodyChunk = HtmlChunk.tag("body")
      .children(
        cardHeaderChunk,
        wrappedBodyContent,
        invisibleSpan
      )

    val htmlContent = HtmlChunk.tag("html").children(headChunk, bodyChunk)
    val htmlString = htmlContent.toString()
    return htmlString
  }

  private fun generateCardHeader(modelInfo: HuggingFaceEntityBasicApiData): HtmlChunk {
    val modelNameWithIconRow = HtmlChunk.tag("h1")
      .attr("style", "white-space: nowrap; word-break: keep-all;")
      .children(
        HtmlChunk.raw(modelInfo.itemId.replace("-", HuggingFaceQuickDocStyles.NBHP)),
        HtmlChunk.nbsp(),
        HtmlChunk.text(HuggingFaceConstants.HF_EMOJI)
      )

    // modelPurpose chunk is not applicable for datasets
    val conditionalChunks = if (entityKind == HuggingFaceEntityKind.MODEL) {
      @NlsSafe val modelPurpose = modelInfo.pipelineTag
      listOf(HtmlChunk.text(modelPurpose), HtmlChunk.nbsp(2))
    } else {
      emptyList()
    }

    val modelInfoRow = HtmlChunk.span()
      .setClass(HuggingFaceQuickDocStyles.HF_GRAYED_CLASS)
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


    val headerContainer = HtmlChunk.div()
      .setClass(DocumentationMarkup.CLASS_DEFINITION)
      .style("min-width: 1000px; ")
      .children(
        modelNameWithIconRow,
        modelInfoRow,
        linkRow,
      )
    return headerContainer
  }

  private fun createInvisibleSpan(convertedHtml: String): HtmlChunk {
    // artificially increase content length, if it's too little
    // is needed to get an adequate popup width
    val htmlTagRegex = "<[^>]*>".toRegex()
    val contentWithoutHtmlTags = convertedHtml.replace(htmlTagRegex, "")
    val contentLength = contentWithoutHtmlTags.count()

    val invisibleSpanLength = (800 - contentLength) / 8

    return when {
      invisibleSpanLength < 0 -> HtmlChunk.empty()
      else -> {
        @NlsSafe val invisibleSpan = HtmlChunk.nbsp(invisibleSpanLength)
        return invisibleSpan
      }
    }
  }
}
