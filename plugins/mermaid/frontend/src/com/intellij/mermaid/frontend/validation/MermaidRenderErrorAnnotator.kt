// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.frontend.validation

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.psi.MermaidFile
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Reports what the bundled mermaid itself rejects, on top of what our grammar can tell.
 *
 * A plain [Annotator], and frontend-owned, because the verdict comes from the JCEF preview: in split mode the
 * browser lives in the client, while `ExternalAnnotator` -- whose off-EDT `doAnnotate` is otherwise exactly
 * the shape of this work -- is a backend-only API, and a backend has no preview to ask. The waiting therefore
 * moved out of the daemon into [MermaidRenderErrorCache]; this class only reads the last answer, which is
 * cheap enough to do synchronously.
 *
 * Severity is a weak warning, not an error, and that is deliberate. mermaid's parser has bugs of its own --
 * its own block documentation contains `style A fill#969;`, a declaration with no colon, which it happily
 * accepts -- so treating its complaints as errors would import its false positives and recreate the problem
 * this plugin has been digging itself out of. A weak warning says "the renderer will probably not like this"
 * without claiming the document is broken.
 */
internal class MermaidRenderErrorAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    // The file element, which the daemon always visits, and only it: mermaid judges the diagram as a whole
    // and numbers its lines from the start of the file, while an annotation has to stay inside the element
    // it is reported on.
    val file = element as? MermaidFile ?: return
    val document = file.viewProvider.document ?: return

    for (problem in file.project.service<MermaidRenderErrorCache>().problemsFor(file, document)) {
      // Falling back to the diagram's first line rather than the whole file: mermaid does not always say
      // where the problem is, and underlining an entire document reads as "everything here is wrong".
      val range = problem.line?.let { lineRange(document, it) }
                  ?: lineRange(document, 1)
                  ?: file.textRange
      holder
        .newAnnotation(HighlightSeverity.WEAK_WARNING, MermaidBundle.message("annotator.render.error", problem.message))
        .range(range)
        .create()
    }
  }

  /**
   * The 1-based [line] as mermaid counts them, narrowed to its non-blank content so the squiggle sits under
   * the code rather than the indentation. Null when the line is out of range or blank.
   */
  private fun lineRange(document: Document, line: Int): TextRange? {
    val index = line - 1
    if (index < 0 || index >= document.lineCount) return null

    val start = document.getLineStartOffset(index)
    val end = document.getLineEndOffset(index)
    val text = document.charsSequence
    var from = start
    while (from < end && text[from].isWhitespace()) from++
    var to = end
    while (to > from && text[to - 1].isWhitespace()) to--
    return if (from < to) TextRange.create(from, to) else null
  }
}
