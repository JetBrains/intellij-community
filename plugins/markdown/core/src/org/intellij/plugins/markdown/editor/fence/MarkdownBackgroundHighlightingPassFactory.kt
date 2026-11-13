// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.fence

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.codeHighlighting.Pass
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parents
import org.intellij.plugins.markdown.highlighting.MarkdownHighlighterColors
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.isMarkdownLanguage
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownAlert
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownBlockQuote
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeSpan
import org.jetbrains.annotations.ApiStatus

private val BACKGROUND_HIGHLIGHTERS = Key.create<List<RangeHighlighter>>("markdown.background.highlighters")

/**
 * Paint one rounded background behind the code of a Markdown code fence, behind the content of a code span, and behind a block quote.
 *
 * [MarkdownCodeFence] suppresses the background of the injection, so this pass owns the background of
 * every fence. The background of the injection follows the injected ranges. Those ranges leave out the
 * indent of a list or of a block quote, and they cannot cover a blank line, because such a line holds
 * no character in the column of the code. One rectangle solves both cases. It also gives a fence
 * without a language the same background as a fence with one.
 *
 * The rectangle of a fence starts in the column of the code and ends at the right edge of the editor. It
 * covers the lines from the opening fence marker to the closing fence marker. It takes the background color of
 * [MarkdownHighlighterColors.CODE_FENCE], and falls back to the color of the injected fragment.
 *
 * The rectangle of a code span covers the content between the backtick runs. It takes the background
 * color of [MarkdownHighlighterColors.CODE_SPAN].
 *
 * The shape of a block quote covers the lines of an outermost quote or alert, and ends at the right edge of the editor.
 * Its left corners are square. It takes the background color of [MarkdownHighlighterColors.BLOCK_QUOTE].
 */
@Suppress("SplitModeApiUsage")
internal class MarkdownBackgroundHighlightingPassFactory :
  TextEditorHighlightingPassFactoryRegistrar, TextEditorHighlightingPassFactory, DumbAware {
  override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
    registrar.registerTextEditorHighlightingPass(
      this, TextEditorHighlightingPassRegistrar.Anchor.AFTER, Pass.UPDATE_ALL, false, false,
    )
  }

  override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
    if (!file.language.isMarkdownLanguage()) return null
    // A split-mode frontend gets these highlighters from the backend.
    if (IdeProductMode.isFrontend && !IdeProductMode.isLight) return null
    return BackgroundPass(editor, file)
  }

  private class BackgroundPass(editor: Editor, file: PsiFile) : EditorBoundHighlightingPass(editor, file, false), DumbAware {
    private var elements = emptyList<Pair<MarkdownBackgroundRenderer, TextRange>>()

    override fun doCollectInformation(progress: ProgressIndicator) {
      elements = PsiTreeUtil.findChildrenOfAnyType(
        myFile, MarkdownCodeFence::class.java, MarkdownCodeSpan::class.java, MarkdownBlockQuote::class.java, MarkdownAlert::class.java,
      ).mapNotNull { element ->
        progress.checkCanceled()
        when (element) {
          is MarkdownCodeFence -> collectCodeFenceBackground(element)?.let { MarkdownBackgroundRenderer.CODE_FENCE to it }
          is MarkdownCodeSpan -> collectCodeSpanBackground(element)?.let { MarkdownBackgroundRenderer.CODE_SPAN to it }
          is MarkdownBlockQuote, is MarkdownAlert -> collectBlockQuoteBackground(element)?.let { MarkdownBackgroundRenderer.BLOCK_QUOTE to it }
          else -> null
        }
      }
    }

    override fun doApplyInformationToEditor() {
      val markupModel = myEditor.markupModel as MarkupModelEx
      val missing = elements.toMutableSet()
      val highlighters = ArrayList<RangeHighlighter>()
      for (highlighter in myEditor.getUserData(BACKGROUND_HIGHLIGHTERS).orEmpty()) {
        if (highlighter.isValid && missing.remove(highlighter.customRenderer to highlighter.textRange)) {
          highlighters.add(highlighter)
        }
        else {
          markupModel.removeHighlighter(highlighter)
        }
      }
      missing.mapTo(highlighters) { [renderer, range] ->
        markupModel.addRangeHighlighterAndChangeAttributes(
          renderer.highlighterKey, range.startOffset, range.endOffset, HighlighterLayer.ADDITIONAL_SYNTAX,
          HighlighterTargetArea.EXACT_RANGE, false,
        ) { it.customRenderer = renderer }
      }
      myEditor.putUserData(BACKGROUND_HIGHLIGHTERS, highlighters)
    }
  }
}

/**
 * Collect the range of the background of [fence]: from the opening marker to the closing marker, or to the end of a fence without one.
 * The start is in the column of the code, which gives the left side. Return null when the fence has no opening marker.
 */
@ApiStatus.Internal
fun collectCodeFenceBackground(fence: MarkdownCodeFence): TextRange? {
  val opening = fence.children.firstOrNull { it.elementType == MarkdownTokenTypes.CODE_FENCE_START } ?: return null
  val closing = fence.children.lastOrNull { it.elementType == MarkdownTokenTypes.CODE_FENCE_END }
  // A content token starts in the column of the code, and so does the opening marker. The marker is
  // always there, and a fence that holds only blank lines has no content token.
  val start = opening.textRange.startOffset + MarkdownCodeFenceUtils.getIndentationInfo(opening.text).length
  return TextRange(start, (closing ?: fence).textRange.endOffset)
}

/**
 * Collect the range of the background of [span]: the content between its backtick runs, with the spaces next to the
 * runs. Return null when the span has no closing run or no content.
 */
@ApiStatus.Internal
fun collectCodeSpanBackground(span: MarkdownCodeSpan): TextRange? {
  val node = span.node
  val opening = node.firstChildNode?.takeIf { it.elementType == MarkdownTokenTypes.BACKTICK } ?: return null
  val closing = node.lastChildNode?.takeIf { it != opening && it.elementType == MarkdownTokenTypes.BACKTICK } ?: return null
  return TextRange(opening.startOffset + opening.textLength, closing.startOffset).takeUnless { it.isEmpty }
}

/**
 * Collect the range of the background of [quote], a block quote or an alert: from its opening marker to its end.
 * Return null when [quote] is inside another quote, because the background of the outer quote covers it.
 */
@ApiStatus.Internal
fun collectBlockQuoteBackground(quote: PsiElement): TextRange? =
  quote.textRange.takeUnless { quote.parents(withSelf = false).any { it is MarkdownBlockQuote || it is MarkdownAlert } }
