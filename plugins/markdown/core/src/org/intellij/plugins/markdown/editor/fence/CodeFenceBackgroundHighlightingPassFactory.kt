// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.fence

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.codeHighlighting.Pass
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import org.intellij.plugins.markdown.highlighting.MarkdownHighlighterColors
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.isMarkdownLanguage
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeSpan
import org.jetbrains.annotations.ApiStatus

private val CODE_FENCE_BACKGROUND_HIGHLIGHTERS = Key.create<List<RangeHighlighter>>("markdown.code.fence.background.highlighters")

/**
 * Paint one rounded rectangle behind the code of a Markdown code fence, and one behind the content of a code span.
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
 * color of [MarkdownHighlighterColors.CODE_SPAN]. The text attributes of a fence and of a code span carry
 * no background, so only the rounded rectangle paints one.
 */
@Suppress("SplitModeApiUsage")
internal class CodeFenceBackgroundHighlightingPassFactory :
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
    return CodeFenceBackgroundPass(editor, file)
  }

  private class CodeFenceBackgroundPass(editor: Editor, file: PsiFile) : EditorBoundHighlightingPass(editor, file, false), DumbAware {
    private var elements = emptyList<Pair<TextAttributesKey, TextRange>>()

    override fun doCollectInformation(progress: ProgressIndicator) {
      elements = PsiTreeUtil.findChildrenOfAnyType(myFile, MarkdownCodeFence::class.java, MarkdownCodeSpan::class.java).mapNotNull { element ->
        progress.checkCanceled()
        when (element) {
          is MarkdownCodeFence -> collectCodeFenceBackground(element)?.let { MarkdownHighlighterColors.CODE_FENCE_BACKGROUND to it }
          is MarkdownCodeSpan -> collectCodeSpanBackground(element)?.let { MarkdownHighlighterColors.CODE_SPAN_BACKGROUND to it }
          else -> null
        }
      }
    }

    override fun doApplyInformationToEditor() {
      myEditor.getUserData(CODE_FENCE_BACKGROUND_HIGHLIGHTERS)?.forEach(myEditor.markupModel::removeHighlighter)
      val highlighters = elements.map { [key, range] ->
        myEditor.markupModel.addRangeHighlighter(key, range.startOffset, range.endOffset, HighlighterLayer.ADDITIONAL_SYNTAX, HighlighterTargetArea.EXACT_RANGE).also {
          it.customRenderer = if (key == MarkdownHighlighterColors.CODE_FENCE_BACKGROUND) MarkdownCodeFenceBackgroundRenderer() else MarkdownCodeSpanBackgroundRenderer()
        }
      }
      myEditor.putUserData(CODE_FENCE_BACKGROUND_HIGHLIGHTERS, highlighters)
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
