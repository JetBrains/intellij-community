/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.annotator

import com.intellij.ide.annotator.AnnotatorBase
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SyntaxTraverser
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlInlineTable

class TomlAnnotator : AnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        if (element is TomlInlineTable) {
            val whiteSpaces = SyntaxTraverser.psiTraverser(element)
                .expand { it !is TomlArray } // An array can be multiline even inside an inline table
                .filterIsInstance<PsiWhiteSpace>()
            if (whiteSpaces.any { it.textContains('\n') }) {
                holder.newAnnotation(HighlightSeverity.ERROR, "Inline tables are intended to appear on a single line").create()
            }
        }
    }
}
