// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.surroundWith

import com.intellij.debugger.DebuggerInvocationUtil
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.command.WriteCommandAction.writeCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinDebuggerEvaluationBundle
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinRuntimeTypeEvaluator
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker


class KotlinRuntimeTypeCastSurrounder : Surrounder {

    override fun isApplicable(elements: Array<PsiElement>): Boolean {
        if (elements.size != 1 || elements[0] !is KtExpression) {
            return false
        }
        val expression = elements[0] as KtExpression
        if (expression is KtCallExpression && expression.getParent() is KtQualifiedExpression) {
            return false
        }

        if (!expression.isPhysical) return false
        val file = expression.containingFile
        if (file !is KtCodeFragment) return false

        val type = expression.analyze(BodyResolveMode.PARTIAL).getType(expression) ?: return false

        return TypeUtils.canHaveSubtypes(KotlinTypeChecker.DEFAULT, type)
    }

    override fun surroundElements(project: Project, editor: Editor, elements: Array<PsiElement>): TextRange? {
        val expression = elements.singleOrNull() as? KtExpression ?: return null
        val debuggerContext = DebuggerManagerEx.getInstanceEx(project).context
        val debuggerSession = debuggerContext.debuggerSession
        if (debuggerSession != null) {
            val progressWindow = ProgressWindow(true, expression.project)
            val worker = SurroundWithCastWorker(editor, expression, debuggerContext, progressWindow)
            progressWindow.title = JavaDebuggerBundle.message("title.evaluating")
            debuggerContext.managerThread?.startProgress(worker, progressWindow)
        }
        return null
    }

    @Suppress("DialogTitleCapitalization")
    override fun getTemplateDescription(): String {
        return KotlinDebuggerEvaluationBundle.message("surround.with.runtime.type.cast.template")
    }

    private inner class SurroundWithCastWorker(
        private val myEditor: Editor,
        expression: KtExpression,
        context: DebuggerContextImpl,
        indicator: ProgressIndicator
    ) : KotlinRuntimeTypeEvaluator(myEditor, expression, context, indicator) {

        override fun typeCalculationFinished(type: KotlinType?) {
            if (type == null) return

            hold()

            val project = myEditor.project
            DebuggerInvocationUtil.invokeLater(project, Runnable {
                writeCommandAction(project).withName(JavaDebuggerBundle.message("command.name.surround.with.runtime.cast")).run<Nothing> {
                    try {
                        val factory = KtPsiFactory(myElement.project)

                        val fqName = DescriptorUtils.getFqName(type.constructor.declarationDescriptor!!)
                        val parentCast = factory.createExpression("(expr as " + fqName.asString() + ")") as KtParenthesizedExpression
                        val cast = parentCast.expression as KtBinaryExpressionWithTypeRHS
                        cast.left.replace(myElement)
                        val expr = myElement.replace(parentCast) as KtExpression

                        ShortenReferences.DEFAULT.process(expr)

                        val range = expr.textRange
                        myEditor.selectionModel.setSelection(range.startOffset, range.endOffset)
                        myEditor.caretModel.moveToOffset(range.endOffset)
                        myEditor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
                    } finally {
                        release()
                    }
                }
            }, myProgressIndicator.modalityState)
        }

    }
}
