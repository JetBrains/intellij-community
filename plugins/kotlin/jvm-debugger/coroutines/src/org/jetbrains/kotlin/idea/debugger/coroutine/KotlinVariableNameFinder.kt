// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.coroutine

import com.intellij.debugger.NoDataException
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parents
import com.intellij.psi.util.parentsOfType
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.sun.jdi.Location
import org.jetbrains.kotlin.backend.common.descriptors.isSuspend
import org.jetbrains.kotlin.builtins.isSuspendFunctionType
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.debugger.KotlinPositionManager
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.inline.InlineUtil
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

internal class KotlinVariableNameFinder(val debugProcess: DebugProcessImpl) {
    @RequiresReadLock
    fun findVisibleVariableNames(location: Location): List<String> {
        val sourcePosition = location.safeSourcePosition(debugProcess) ?: return emptyList()
        ProgressManager.checkCanceled()
        return sourcePosition.elementAt.findVisibleVariableNames()
    }

    private fun PsiElement.findVisibleVariableNames(): List<String> {
        val enclosingBlockExpression = findEnclosingBlockExpression() ?: return emptyList()
        val blockParents = enclosingBlockExpression.parentsOfType<KtBlockExpression>()
        if (blockParents.none()) {
            return emptyList()
        }

        ProgressManager.checkCanceled()
        val bindingContext = blockParents.last().analyze(BodyResolveMode.PARTIAL)

        ProgressManager.checkCanceled()
        val expressionToStartAnalysisFrom =
            enclosingBlockExpression.findExpressionToStartAnalysisFrom(bindingContext)
        if (!expressionToStartAnalysisFrom.isCoroutineContextAvailable(bindingContext)) {
            return emptyList()
        }

        ProgressManager.checkCanceled()
        val parentFunction = expressionToStartAnalysisFrom.parentOfType<KtFunction>(true)
        val namesInParameterList =
            parentFunction?.findVariableNamesInParameterList() ?: emptyList()
        val namesVisibleInExpression = expressionToStartAnalysisFrom.findVariableNames(
            bindingContext, this, blockParents
        )
        return namesVisibleInExpression + namesInParameterList
    }

    private fun KtExpression.findVariableNames(
        bindingContext: BindingContext,
        boundaryElement: PsiElement,
        blocksToVisit: Sequence<KtBlockExpression>
    ): List<String> {
        val names = mutableListOf<String>()
        accept(object : KtTreeVisitorVoid() {
            var stopTraversal = false

            override fun visitBlockExpression(expression: KtBlockExpression) {
                if (expression.isInlined(bindingContext) || expression in blocksToVisit) {
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

    private fun KtFunction.findVariableNamesInParameterList(): List<String> {
        val parameterList = getChildOfType<KtParameterList>() ?: return emptyList()
        return parameterList.parameters.mapNotNull { it.name }
    }

    private fun KtExpression.findExpressionToStartAnalysisFrom(bindingContext: BindingContext): KtExpression {
        var lastSeenBlockExpression = this
        for (parent in parents(true)) {
            when (parent) {
                is KtNamedFunction -> return parent
                is KtBlockExpression -> {
                    if (!parent.isInlined(bindingContext) && parent.parent !is KtWhenEntry) {
                        return parent
                    }
                    lastSeenBlockExpression = parent
                }
            }
        }
        return lastSeenBlockExpression
    }

    private fun KtExpression.isCoroutineContextAvailable(bindingContext: BindingContext) =
        isCoroutineContextAvailableFromFunction() || isCoroutineContextAvailableFromLambda(bindingContext)

    private fun KtExpression.isCoroutineContextAvailableFromFunction(): Boolean {
        val functionParent = parentOfType<KtFunction>(true) ?: return false
        val descriptor = functionParent.descriptor as? CallableDescriptor ?: return false
        return descriptor.isSuspend
    }

    private fun KtExpression.isCoroutineContextAvailableFromLambda(bindingContext: BindingContext): Boolean {
        val type = getType(bindingContext) ?: return false
        return type.isSuspendFunctionType
    }

    private fun PsiElement.findEnclosingBlockExpression(): KtBlockExpression? {
        for (parent in parents(false)) {
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

    private fun KtBlockExpression.isInlined(bindingContext: BindingContext): Boolean {
        val parentFunction = parentOfType<KtFunction>() ?: return false
        return InlineUtil.isInlinedArgument(parentFunction, bindingContext, false)
    }

    private fun Location.safeSourcePosition(debugProcess: DebugProcessImpl) =
        try {
            KotlinPositionManager(debugProcess).getSourcePosition(this)
        } catch (ex: NoDataException) {
            null
        }
}
