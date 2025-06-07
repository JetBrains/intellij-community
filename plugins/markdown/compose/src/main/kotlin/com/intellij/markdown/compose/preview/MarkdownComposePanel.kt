// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.compose.preview

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefPsiNavigationUtils
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelEx
import org.intellij.plugins.markdown.ui.preview.MarkdownUpdateHandler
import org.intellij.plugins.markdown.ui.preview.MarkdownUpdateHandler.PreviewRequest
import org.intellij.plugins.markdown.ui.preview.PreviewStyleScheme
import org.intellij.plugins.markdown.ui.preview.accessor.MarkdownLinkOpener
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.bridge.code.highlighting.CodeHighlighterFactory
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.code.highlighting.NoOpCodeHighlighter
import org.jetbrains.jewel.intui.markdown.bridge.ProvideMarkdownStyling
import org.jetbrains.jewel.intui.markdown.bridge.styling.extensions.github.tables.create
import org.jetbrains.jewel.markdown.Markdown
import org.jetbrains.jewel.markdown.MarkdownMode
import org.jetbrains.jewel.markdown.extensions.autolink.AutolinkProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.strikethrough.GitHubStrikethroughProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.strikethrough.GitHubStrikethroughRendererExtension
import org.jetbrains.jewel.markdown.extensions.github.tables.GfmTableStyling
import org.jetbrains.jewel.markdown.extensions.github.tables.GitHubTableProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.tables.GitHubTableRendererExtension
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.DefaultInlineMarkdownRenderer
import org.jetbrains.jewel.markdown.scrolling.ScrollSyncMarkdownBlockRenderer
import org.jetbrains.jewel.markdown.scrolling.ScrollingSynchronizer
import javax.swing.JComponent
import kotlin.time.Duration.Companion.milliseconds

@ExperimentalJewelApi
internal class MarkdownComposePanel(
  private val project: Project?,
  private val virtualFile: VirtualFile?,
  private val updateHandler: MarkdownUpdateHandler = MarkdownUpdateHandler.Debounced()
) : MarkdownHtmlPanelEx, UserDataHolder by UserDataHolderBase() {

  constructor() : this(null, null)

  private val scrollToLineFlow = MutableSharedFlow<Int>(replay = 1)

  private val panelComponent by lazy {
    JewelComposePanel {
      // TODO temporary styling, we will likely need our own in the future for JCEF-like rendering
      MarkdownPanel()
    }
  }

  @Suppress("FunctionName")
  @Composable
  private fun MarkdownPanel() {
    val scheme = PreviewStyleScheme.fromCurrentTheme()
    val fontSize = scheme.fontSize.sp / scheme.scale
    val scrollState = rememberScrollState(0)
    val scrollingSynchronizer = remember(scrollState) { ScrollingSynchronizer.create(scrollState) }
    val markdownStyling = remember(scheme, fontSize) { JcefLikeMarkdownStyling(scheme, fontSize) }
    val markdownMode = remember(scrollingSynchronizer) {
      MarkdownMode.EditorPreview(scrollingSynchronizer)
    }
    val processor = remember(markdownMode) {
      MarkdownProcessor(
        listOf(
          GitHubTableProcessorExtension,
          GitHubStrikethroughProcessorExtension(),
          AutolinkProcessorExtension,
        ),
        markdownMode,
      )
    }
    val tableRenderer = remember(markdownStyling) {
      GitHubTableRendererExtension(GfmTableStyling.create(), markdownStyling)
    }
    val allRenderingExtensions = listOf(tableRenderer, GitHubStrikethroughRendererExtension)
    val blockRenderer = remember(markdownStyling) {
      ScrollSyncMarkdownBlockRenderer(
        markdownStyling,
        allRenderingExtensions,
        DefaultInlineMarkdownRenderer(allRenderingExtensions),
      )
    }
    ProvideMarkdownStyling(
      markdownMode = markdownMode,
      markdownProcessor = processor,
      markdownStyling = markdownStyling,
      codeHighlighter = remember(project) {
        project?.let {
          CodeHighlighterFactory.getInstance(project).createHighlighter()
        } ?: NoOpCodeHighlighter
      },
      markdownBlockRenderer = blockRenderer
    ) {
      Box(
        modifier = Modifier
          .background(scheme.backgroundColor.toComposeColor())
          .padding(horizontal = fontSize.value.dp * 2)
      ) {
        MarkdownPreviewPanel(scrollState, scrollingSynchronizer, blockRenderer)
        VerticalScrollbar(
          modifier = Modifier
            .align(Alignment.CenterEnd),
          adapter = rememberScrollbarAdapter(scrollState),
        )
      }
    }
  }

  @OptIn(FlowPreview::class)
  @Suppress("FunctionName")
  @Composable
  private fun MarkdownPreviewPanel(scrollState: ScrollState,
                                   scrollingSynchronizer: ScrollingSynchronizer?,
                                   blockRenderer: ScrollSyncMarkdownBlockRenderer,
                                   animationSpec: AnimationSpec<Float> = TweenSpec(easing = LinearEasing)
  ) {
    val request by updateHandler.requests.collectAsState(null)
    (request as? PreviewRequest.Update)?.let {
      if (scrollingSynchronizer != null) {
        LaunchedEffect(Unit) {
          // wait until the preview finished composing
          withFrameNanos { }
          scrollToLineFlow.debounce(1.milliseconds).collectLatest { scrollToLine ->
            scrollingSynchronizer.scrollToLine(scrollToLine, animationSpec)
          }
        }
        LaunchedEffect(it.initialScrollOffset) {
          // wait until the preview finished composing
          withFrameNanos { }
          if (it.initialScrollOffset != 0) {
            scrollToLineFlow.emit(it.initialScrollOffset)
          }
        }
      }
      Markdown(
        it.content,
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(scrollState),
        enabled = true,
        selectable = true,
        onUrlClick = { url ->
          if (!Registry.`is`("markdown.open.link.in.external.browser")) return@Markdown
          if (JBCefPsiNavigationUtils.navigateTo(url)) return@Markdown

          if (Registry.`is`("markdown.open.link.fallback"))
            MarkdownLinkOpener.getInstance().openLink(project, url)
          else
            MarkdownLinkOpener.getInstance().openLink(project, url, virtualFile)
                     },
        blockRenderer = blockRenderer,
      )
    }
  }

  override fun setHtml(html: String, initialScrollOffset: Int, document: VirtualFile?) {
  }

  override fun setHtml(html: String, initialScrollOffset: Int, initialScrollLineNumber: Int, document: VirtualFile?) {
    updateHandler.setContent(html, initialScrollLineNumber, document)
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

  override suspend fun scrollTo(editor: Editor, line: Int) {
    scrollToLineFlow.emit(line)
  }

  override fun scrollBy(horizontalUnits: Int, verticalUnits: Int) {
  }
}
