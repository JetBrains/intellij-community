// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parents
import com.intellij.psi.util.parentsOfType
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.sun.jdi.Location
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.psi.getContainingValueArgument
import org.jetbrains.kotlin.idea.codeinsight.utils.isInlinedArgument
import org.jetbrains.kotlin.idea.debugger.KotlinPositionManager
import org.jetbrains.kotlin.idea.debugger.base.util.runDumbAnalyze
import org.jetbrains.kotlin.idea.debugger.base.util.safeGetSourcePosition
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class KotlinVariableNameFinder(val debugProcess: DebugProcessImpl) {
    @RequiresReadLock
    fun findVisibleVariableNames(location: Location): List<String> {
        val sourcePosition = KotlinPositionManager(debugProcess).safeGetSourcePosition(location) ?: return emptyList()
        ProgressManager.checkCanceled()
        val elementAt = sourcePosition.elementAt ?: return emptyList()
        return findVisibleVariableNamesFrom(elementAt)
    }

    private fun findVisibleVariableNamesFrom(element: PsiElement): List<String> {
        val enclosingBlockExpression = findEnclosingBlockExpression(element) ?: return emptyList()
        val blockParents = enclosingBlockExpression.parentsOfType<KtBlockExpression>()
        if (blockParents.none()) {
            return emptyList()
        }

        ProgressManager.checkCanceled()
        return runDumbAnalyze(enclosingBlockExpression, fallback = emptyList()) f@ {
            val expressionToStartAnalysisFrom = findExpressionToStartAnalysisFrom(enclosingBlockExpression)
            if (!isCoroutineContextAvailable(expressionToStartAnalysisFrom)) {
                return@f emptyList()
            }

            ProgressManager.checkCanceled()
            val parentFunction = expressionToStartAnalysisFrom.parentOfType<KtFunction>(withSelf = true) ?: return@f emptyList()
            val namesInParameterList = findVariableNamesInParameterList(parentFunction)
            val namesVisibleInExpression = findVariableNames(expressionToStartAnalysisFrom, element, blockParents)

            namesVisibleInExpression + namesInParameterList
        }
    }

    context(KaSession)
    private fun findVariableNames(
        expression: KtExpression,
        boundaryElement: PsiElement,
        blocksToVisit: Sequence<KtBlockExpression>
    ): List<String> {
        val names = mutableListOf<String>()
        expression.accept(object : KtTreeVisitorVoid() {
            var stopTraversal = false

            override fun visitBlockExpression(expression: KtBlockExpression) {
                if (expression in blocksToVisit) {
                    expression.acceptChildren(this)
                }
            }

            override fun visitKtElement(element: KtElement) {
                ProgressManager.checkCanceled()
                when {
                    stopTraversal -> return
                    element.startOffset >= boundaryElement.startOffset -> {
                        stopTraversal = true
                        return
                    }
                    element is KtVariableDeclaration &&
                    !element.shouldBeFiltered(boundaryElement) -> {
                        element.name?.let { names.add(it) }
                    }
                }
                element.acceptChildren(this)
            }
        })

        return names
    }

    private fun findVariableNamesInParameterList(function: KtFunction): List<String> {
        val parameterList = function.getChildOfType<KtParameterList>() ?: return emptyList()
        return parameterList.parameters.mapNotNull { it.name }
    }

    context(KaSession)
private fun findExpressionToStartAnalysisFrom(expression: KtExpression): KtExpression {
        var lastSeenBlockExpression = expression
        for (parent in expression.parents(withSelf = true)) {
            when (parent) {
                is KtNamedFunction -> return parent
                is KtBlockExpression -> {
                    if (!isInlined(parent) && parent.parent !is KtWhenEntry) {
                        return parent
                    }
                    lastSeenBlockExpression = parent
                }
            }
        }

        return lastSeenBlockExpression
    }

    context(KaSession)
    private fun isCoroutineContextAvailable(expression: KtExpression) =
        isCoroutineContextAvailableFromFunction(expression) || isCoroutineContextAvailableFromLambda(expression)

    context(KaSession)
    private fun isCoroutineContextAvailableFromFunction(expression: KtExpression): Boolean {
        val functionParent = expression.parentOfType<KtFunction>(withSelf = true) ?: return false
        val symbol = functionParent.symbol as? KaNamedFunctionSymbol ?: return false
        return symbol.isSuspend
    }

    context(KaSession)
    private fun isCoroutineContextAvailableFromLambda(expression: KtExpression): Boolean {
        val literalParent = expression.parentOfType<KtFunctionLiteral>(withSelf = true) ?: return false
        val parentCall = KtPsiUtil.getParentCallIfPresent(literalParent) as? KtCallExpression ?: return false
        val call = parentCall.resolveToCall()?.singleFunctionCallOrNull() ?: return false
        val valueArgument = parentCall.getContainingValueArgument(expression) ?: return false
        val argumentSymbol = call.argumentMapping[valueArgument.getArgumentExpression()]?.symbol ?: return false
        return argumentSymbol.returnType.isSuspendFunctionType
    }

    private fun findEnclosingBlockExpression(element: PsiElement): KtBlockExpression? {
        for (parent in element.parents(withSelf = false)) {
            when (parent) {
                is KtFunction, is KtWhenEntry, is KtLambdaExpression ->
                    return parent.getChildOfType()
                is KtBlockExpression ->
                    return parent
            }
        }

        return null
    }

    private fun KtDeclaration.shouldBeFiltered(elementAtBreakpointPosition: PsiElement) =
        if (parent is KtWhenExpression) {
            parent !in elementAtBreakpointPosition.parents(false)
        } else {
            false
        }

    context(KaSession)
    private fun isInlined(expression: KtBlockExpression): Boolean {
        val parentFunction = expression.parentOfType<KtFunction>() ?: return false
        return isInlinedArgument(parentFunction)
    }
}
