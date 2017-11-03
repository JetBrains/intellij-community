/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.toml.lang.psi.TomlInlineTable

class TomlAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element is TomlInlineTable) {
            val whiteSpaces = PsiTreeUtil.findChildrenOfType(element, PsiWhiteSpace::class.java)
            if (whiteSpaces.any { it.textContains('\n') }) {
                holder.createErrorAnnotation(element, "Inline tables are intended to appear on a single line")
            }
        }
    }
}
