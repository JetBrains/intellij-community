/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlVisitor
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

class TomlUnresolvedReferenceInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : TomlVisitor() {
            override fun visitLiteral(element: TomlLiteral) {
                if (element.kind !is TomlLiteralKind.String) return
                checkReference(element, holder)
            }
        }
    }

    private fun checkReference(element: PsiElement, holder: ProblemsHolder) {
        element.references
            .filter { it.resolve() == null }
            .forEach { holder.registerProblem(it, ProblemsHolder.unresolvedReferenceMessage(it), ProblemHighlightType.LIKE_UNKNOWN_SYMBOL) }
    }
}
