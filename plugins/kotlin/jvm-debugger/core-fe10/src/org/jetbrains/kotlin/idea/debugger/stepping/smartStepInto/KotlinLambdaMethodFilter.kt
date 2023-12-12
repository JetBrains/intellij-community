// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.BreakpointStepMethodFilter
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.parentOfType
import com.intellij.util.Range
import com.sun.jdi.Location
import org.jetbrains.kotlin.codegen.coroutines.INVOKE_SUSPEND_METHOD_NAME
import org.jetbrains.kotlin.idea.base.psi.isMultiLine
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.idea.debugger.breakpoints.inTheMethod
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils.isGeneratedIrBackendLambdaMethodName
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils.trimIfMangledInBytecode
import org.jetbrains.kotlin.idea.debugger.core.stepping.StopOnReachedMethodFilter
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer

class KotlinLambdaMethodFilter(
    lambda: KtFunction,
    private val callingExpressionLines: Range<Int>?,
    private val lambdaInfo: KotlinLambdaInfo
) : BreakpointStepMethodFilter, StopOnReachedMethodFilter {
    private val lambdaPtr = lambda.createSmartPointer()
    private val firstStatementPosition: SourcePosition?
    private val lastStatementLine: Int

    init {
        val (firstPosition, lastPosition) = findFirstAndLastStatementPositions(lambda)
        firstStatementPosition = firstPosition
        lastStatementLine = lastPosition?.line ?: -1
    }

    override fun getBreakpointPosition() = firstStatementPosition

    override fun getLastStatementLine() = lastStatementLine

    override fun locationMatches(process: DebugProcessImpl, location: Location): Boolean {
        val lambda = lambdaPtr.getElementInReadAction() ?: return true
        if (lambdaInfo.isInline) {
            return location.matchesLambda(process, lambda)
        }

        val method = location.safeMethod() ?: return true
        if (method.isBridge) {
            return false
        }

        val methodName = method.name() ?: return false
        return isTargetLambdaName(methodName) && location.matchesLambda(process, lambda)
    }

    private fun Location.matchesLambda(process: DebugProcessImpl, lambda: KtFunction): Boolean {
        val sourcePosition = process.positionManager.getSourcePosition(this) ?: return true
        return runReadAction { inTheMethod(sourcePosition, lambda) }
    }

    override fun getCallingExpressionLines() = callingExpressionLines

    fun isTargetLambdaName(name: String): Boolean {
        val actualName = name.trimIfMangledInBytecode(lambdaInfo.isNameMangledInBytecode)
        if (lambdaInfo.isSuspend) {
            return actualName == INVOKE_SUSPEND_METHOD_NAME
        }
        return actualName == lambdaInfo.methodName || actualName.isGeneratedIrBackendLambdaMethodName()
    }
}

fun findFirstAndLastStatementPositions(declaration: KtDeclarationWithBody): Pair<SourcePosition?, SourcePosition?> {
    val body = declaration.bodyExpression
    if (body != null && declaration.isMultiLine() && body.children.isNotEmpty()) {
        var firstStatementPosition: SourcePosition? = null
        var lastStatementPosition: SourcePosition? = null
        val statements = (body as? KtBlockExpression)?.statements ?: listOf(body)
        if (statements.isNotEmpty()) {
            firstStatementPosition = SourcePosition.createFromElement(statements.first())
            if (firstStatementPosition != null) {
                val lastStatement = statements.last()
                lastStatementPosition = SourcePosition.createFromOffset(
                    firstStatementPosition.file,
                    lastStatement.textRange.endOffset
                )
            }
        }
        return Pair(firstStatementPosition, lastStatementPosition)
    }
    val position = SourcePosition.createFromElement(declaration)
    return Pair(position, position)
}
