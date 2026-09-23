// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.editor.fence

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.codeHighlighting.Pass
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import org.intellij.plugins.markdown.highlighting.MarkdownHighlighterColors
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.isMarkdownLanguage
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics

private val CODE_FENCE_BACKGROUND_HIGHLIGHTERS = Key.create<List<RangeHighlighter>>("markdown.code.fence.background.highlighters")

/**
 * Paint one rectangle behind the code of a Markdown code fence.
 *
 * [MarkdownCodeFence] suppresses the background of the injection, so this pass owns the background of
 * every fence. The background of the injection follows the injected ranges. Those ranges leave out the
 * indent of a list or of a block quote, and they cannot cover a blank line, because such a line holds
 * no character in the column of the code. One rectangle solves both cases. It also gives a fence
 * without a language the same background as a fence with one.
 *
 * The rectangle starts in the column of the code and ends at the right edge of the editor. It covers
 * the lines between the two fence markers. It takes the background color of
 * [MarkdownHighlighterColors.CODE_FENCE], and falls back to the color of the injected fragment.
 */
internal class CodeFenceBackgroundHighlightingPassFactory :
  TextEditorHighlightingPassFactoryRegistrar, TextEditorHighlightingPassFactory, DumbAware {
  override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
    registrar.registerTextEditorHighlightingPass(
      this, TextEditorHighlightingPassRegistrar.Anchor.AFTER, Pass.UPDATE_ALL, false, false,
    )
  }

  override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
    if (!file.language.isMarkdownLanguage()) return null
    return CodeFenceBackgroundPass(editor, file)
  }

  private class CodeFenceBackgroundPass(editor: Editor, file: PsiFile) : EditorBoundHighlightingPass(editor, file, false) {
    private var backgrounds = emptyList<CodeFenceBackground>()

    override fun doCollectInformation(progress: ProgressIndicator) {
      backgrounds = PsiTreeUtil.findChildrenOfType(myFile, MarkdownCodeFence::class.java).mapNotNull { fence ->
        progress.checkCanceled()
        collectCodeFenceBackground(fence, myDocument)
      }
    }

    override fun doApplyInformationToEditor() {
      myEditor.getUserData(CODE_FENCE_BACKGROUND_HIGHLIGHTERS)?.forEach(myEditor.markupModel::removeHighlighter)
      val highlighters = backgrounds.map { background ->
        myEditor.markupModel.addRangeHighlighter(
          null, background.startOffset, background.endOffset, HighlighterLayer.ADDITIONAL_SYNTAX, HighlighterTargetArea.EXACT_RANGE,
        ).also { it.customRenderer = CodeFenceBackgroundRenderer(background) }
      }
      myEditor.putUserData(CODE_FENCE_BACKGROUND_HIGHLIGHTERS, highlighters)
    }
  }
}

/**
 * The geometry of the background of one fence.
 *
 * @param codeOffset an offset in the column of the code, which gives the left side
 * @param startOffset the start of the first line of the code
 * @param endOffset the end of the last line of the code
 */
@ApiStatus.Internal
data class CodeFenceBackground(
  val codeOffset: Int,
  val startOffset: Int,
  val endOffset: Int,
)

/** Collect the geometry of the background of [fence], or return null when the fence holds no line of code. */
@ApiStatus.Internal
fun collectCodeFenceBackground(fence: MarkdownCodeFence, document: Document): CodeFenceBackground? {
  val opening = fence.children.firstOrNull { it.elementType == MarkdownTokenTypes.CODE_FENCE_START } ?: return null
  val closing = fence.children.lastOrNull { it.elementType == MarkdownTokenTypes.CODE_FENCE_END }
  val firstLine = document.getLineNumber(opening.textRange.startOffset) + 1
  val lastLine = when (closing) {
    null -> document.getLineNumber(fence.textRange.endOffset)
    else -> document.getLineNumber(closing.textRange.startOffset) - 1
  }
  if (firstLine > lastLine || lastLine >= document.lineCount) return null
  // A content token starts in the column of the code, and so does the opening marker. The marker is
  // always there, and a fence that holds only blank lines has no content token.
  val codeOffset = opening.textRange.startOffset + MarkdownCodeFenceUtils.getIndentationInfo(opening.text).length
  return CodeFenceBackground(
    codeOffset = codeOffset,
    startOffset = document.getLineStartOffset(firstLine),
    endOffset = document.getLineEndOffset(lastLine),
  )
}

private class CodeFenceBackgroundRenderer(private val background: CodeFenceBackground) : CustomHighlighterRenderer {
  override fun paint(editor: Editor, highlighter: RangeHighlighter, graphics: Graphics) {
    val color = backgroundColor(editor) ?: return
    // offsetToXY maps every offset of a collapsed region to the line of its placeholder. A fence that
    // a region hides completely therefore paints as a single row over that placeholder, so skip it. A
    // fence that a region hides only in part keeps its paint, because such a placeholder is itself a
    // line of the code. An expanded region hides nothing, so it needs no check.
    val collapsed = editor.foldingModel.getCollapsedRegionAtOffset(background.codeOffset)
    if (collapsed != null && collapsed.endOffset >= background.endOffset) return
    val left = editor.offsetToXY(background.codeOffset).x
    val width = editor.contentComponent.width - left
    if (width <= 0) return
    val top = editor.offsetToXY(background.startOffset).y
    val bottom = editor.offsetToXY(background.endOffset).y
    graphics.color = color
    var y = top
    while (y <= bottom) {
      graphics.fillRect(left, y, width, editor.lineHeight)
      y += editor.lineHeight
    }
  }

  private fun backgroundColor(editor: Editor): Color? {
    val scheme = editor.colorsScheme
    scheme.getAttributes(MarkdownHighlighterColors.CODE_FENCE)?.backgroundColor?.let { return it }
    val injectedKey = EditorColors.createInjectedLanguageFragmentKey(MarkdownLanguage.INSTANCE)
    return scheme.getAttributes(injectedKey)?.backgroundColor
  }
}
