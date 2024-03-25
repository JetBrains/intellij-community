// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.documentation

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceEntityBasicApiData
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceURLProvider
import com.intellij.python.community.impl.huggingFace.service.PyHuggingFaceBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls


@ApiStatus.Internal
class HuggingFaceHtmlBuilder(
  private val project: Project,
  private val modelDataApiContent: HuggingFaceEntityBasicApiData,
  @Nls private val modelCardContent: String,
  private val entityKind: HuggingFaceEntityKind
) {
  @NlsSafe
  suspend fun build(noHeader: Boolean = false): String {
    val cardHeaderChunk = if (noHeader) {
      HtmlChunk.empty()
    } else {
      generateCardHeader(modelDataApiContent)
    }
    @NlsSafe val convertedHtml = readAction { DocMarkdownToHtmlConverter.convert(project, modelCardContent) }

    val wrappedBodyContent = HtmlChunk.div()
      .setClass(DocumentationMarkup.CLASS_CONTENT)
      .child(HtmlChunk.raw(convertedHtml))

    val bodyChunk = HtmlChunk.tag("body")
      .children(
        cardHeaderChunk,
        wrappedBodyContent,
      )

    val htmlContent = HtmlChunk.tag("html").child(bodyChunk)
    val htmlString = htmlContent.toString()
    return htmlString
  }

  private fun generateCardHeader(modelInfo: HuggingFaceEntityBasicApiData): HtmlChunk {
    val cardTitle = modelInfo.itemId.replace("-", NBHP)
    val modelNameWithIconRow = HtmlChunk.tag("h3").child(HtmlChunk.raw(cardTitle))

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
        HtmlChunk.text(PyHuggingFaceBundle.message("python.hugging.face.updated.suffix", modelInfo.humanReadableLastUpdated)),
        HtmlChunk.nbsp(2),

        DOWNLOADS_ICON,
        HtmlChunk.text(modelInfo.humanReadableDownloads),
        HtmlChunk.nbsp(2),

        LIKES_ICON,
        HtmlChunk.raw(modelInfo.humanReadableLikes),
        HtmlChunk.nbsp(),
      )

    val linkRow = HtmlChunk.tag("a")
      .attr("href", HuggingFaceURLProvider.getEntityCardLink(modelInfo.itemId, entityKind).toString())
      .child(HtmlChunk.text(PyHuggingFaceBundle.getMessage("python.hugging.face.open.on.link.text")))
      .wrapWith("p")

      val headerContainer = HtmlChunk.div()
        .setClass(DocumentationMarkup.CLASS_DEFINITION)
        .children(
          modelNameWithIconRow,
          modelInfoRow,
          linkRow,
        )
    return headerContainer
  }

  companion object {
    private const val NBHP = "&#8209;"
    private val DOWNLOADS_ICON = HtmlChunk.tag("icon")
      .attr("src", "AllIcons.Actions.Download")
    private val LIKES_ICON = HtmlChunk.tag("icon")
      .attr(".src", "AllIcons.Toolwindows.ToolWindowFavorites")
  }
}