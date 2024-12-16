// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.compose.preview

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.compose.JBComposePanel
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelEx
import org.intellij.plugins.markdown.ui.preview.MarkdownUpdateHandler
import org.intellij.plugins.markdown.ui.preview.MarkdownUpdateHandler.PreviewRequest
import org.intellij.plugins.markdown.ui.preview.PreviewStyleScheme
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.bridge.code.highlighting.CodeHighlighterFactory
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.code.highlighting.NoOpCodeHighlighter
import org.jetbrains.jewel.intui.markdown.bridge.ProvideMarkdownStyling
import org.jetbrains.jewel.markdown.Markdown
import javax.swing.JComponent

@ExperimentalJewelApi
internal class MarkdownComposePanel(
  private val project: Project?,
  private val virtualFile: VirtualFile?,
  private val updateHandler: MarkdownUpdateHandler = MarkdownUpdateHandler.Debounced()
) : MarkdownHtmlPanelEx, UserDataHolder by UserDataHolderBase() {

  constructor() : this(null, null)

  private val panelComponent by lazy {
    JBComposePanel {
      // TODO temporary styling, we will likely need our own in the future for JCEF-like rendering
      MarkdownPanel()
    }
  }

  @Suppress("FunctionName")
  @Composable
  private fun MarkdownPanel() {
    val scheme = PreviewStyleScheme.fromCurrentTheme()
    val fontSize = scheme.fontSize.sp / scheme.scale
    ProvideMarkdownStyling(
      markdownStyling = JcefLikeMarkdownStyling(scheme, fontSize),
      codeHighlighter = remember(project) {
        project?.let {
          CodeHighlighterFactory.getInstance(project).createHighlighter()
        } ?: NoOpCodeHighlighter
      },
    ) {
      Box(
        modifier = Modifier
          .background(scheme.backgroundColor.toComposeColor())
          .padding(horizontal = fontSize.value.dp * 2)
      ) {
        val scrollState = rememberScrollState(0)
        MarkdownPreviewPanel(scrollState)
        VerticalScrollbar(
          modifier = Modifier
            .align(Alignment.CenterEnd),
          adapter = rememberScrollbarAdapter(scrollState),
        )
      }
    }
  }

  @Suppress("FunctionName")
  @Composable
  private fun MarkdownPreviewPanel(scrollState: ScrollState) {
    val request by updateHandler.requests.collectAsState(null)
    (request as? PreviewRequest.Update)?.let {
      Markdown(
        it.content,
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(scrollState),
        enabled = true,
        selectable = true,
        onUrlClick = { url -> BrowserUtil.open(url) },
      )
    }
  }

  override fun setHtml(html: String, initialScrollOffset: Int, document: VirtualFile?) {
    updateHandler.setContent(html, initialScrollOffset, document)
  }

  override fun reloadWithOffset(offset: Int) {
    updateHandler.reloadWithOffset(offset)
  }

  override fun getComponent(): JComponent {
    return panelComponent
  }

  override fun dispose() {
  }

  @ApiStatus.Experimental
  override fun getProject(): Project? = project

  @ApiStatus.Experimental
  override fun getVirtualFile(): VirtualFile? = virtualFile

  override fun addScrollListener(listener: MarkdownHtmlPanel.ScrollListener) {
  }

  override fun removeScrollListener(listener: MarkdownHtmlPanel.ScrollListener) {
  }

  override fun scrollToMarkdownSrcOffset(offset: Int, smooth: Boolean) {
  }

  override fun scrollBy(horizontalUnits: Int, verticalUnits: Int) {
  }
}
