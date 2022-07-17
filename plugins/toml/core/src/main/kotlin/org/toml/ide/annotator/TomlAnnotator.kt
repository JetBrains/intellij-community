/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.annotator

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SyntaxTraverser
import org.toml.TomlBundle
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlElementTypes
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.ext.elementType

class TomlAnnotator : AnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        if (element is TomlInlineTable) {
            val whiteSpaces = SyntaxTraverser.psiTraverser(element)
                .expand { it !is TomlArray } // An array can be multiline even inside an inline table
                .filterIsInstance<PsiWhiteSpace>()
            if (whiteSpaces.any { it.textContains('\n') }) {
                holder.newAnnotation(HighlightSeverity.ERROR,
                  TomlBundle.message("inspection.toml.message.inline.tables.on.single.line")).create()
            }
        }

        val parent = element.parent
        if (element.elementType == TomlElementTypes.COMMA && parent is TomlInlineTable &&
            element.textOffset > parent.entries.lastOrNull()?.textOffset ?: 0) {
            val message = TomlBundle.message("intention.toml.name.remove.trailing.comma")

            val fix = object : LocalQuickFix {
                override fun getFamilyName(): String = message
                override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                    descriptor.psiElement?.delete()
                }
            }

            val problemDescriptor = InspectionManager.getInstance(element.project)
                .createProblemDescriptor(element, message, fix, ProblemHighlightType.ERROR, true)

            holder.newAnnotation(HighlightSeverity.ERROR, TomlBundle.message("inspection.toml.message.trailing.commas.in.inline.tables"))
                .newLocalQuickFix(fix, problemDescriptor)
                .registerFix()
                .create()
        }
    }
}
