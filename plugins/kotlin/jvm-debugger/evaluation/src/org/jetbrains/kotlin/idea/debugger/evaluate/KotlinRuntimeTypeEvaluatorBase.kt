// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.DebuggerInvocationUtil
import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.ContextUtil
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.ui.EditorEvaluationCommand
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.sun.jdi.Value
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtPsiFactory

@ApiStatus.Internal
abstract class KotlinRuntimeTypeEvaluatorBase<T>(
    editor: Editor?,
    expression: PsiElement,
    context: DebuggerContextImpl,
    indicator: ProgressIndicator,
) : EditorEvaluationCommand<T>(editor, expression, context, indicator) {

    override fun threadAction(suspendContext: SuspendContextImpl) {
        var type: T? = null
        try {
            type = evaluate()
        } catch (ignored: ProcessCanceledException) {
            throw ignored
        } catch (_: EvaluateException) {
        } finally {
            typeCalculationFinished(type)
        }
    }

    protected abstract fun typeCalculationFinished(type: T?)

    protected abstract fun getCastableRuntimeType(scope: GlobalSearchScope, value: Value): T?

    override fun evaluate(evaluationContext: EvaluationContextImpl): T? {
        val project = evaluationContext.project

        val codeFragment = runReadAction {
            KtPsiFactory(myElement.project).createBlockCodeFragment(
                myElement.text, myElement.containingFile.context
            )
        }
        val evaluator = DebuggerInvocationUtil.commitAndRunReadAction(project) {
            KotlinEvaluatorBuilder.build(codeFragment, ContextUtil.getSourcePosition(evaluationContext))
        }

        val value = evaluator.evaluate(evaluationContext)
        if (value != null) {
            val scope = evaluationContext.debugProcess.searchScope
            return runReadAction {
                getCastableRuntimeType(scope, value)
            }
        }
        throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.surrounded.expression.null"))
    }
}
