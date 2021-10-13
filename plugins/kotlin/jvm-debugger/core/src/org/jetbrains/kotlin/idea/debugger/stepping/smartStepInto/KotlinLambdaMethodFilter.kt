// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.BreakpointStepMethodFilter
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.psi.util.parentOfType
import com.intellij.util.Range
import com.sun.jdi.Location
import org.jetbrains.kotlin.codegen.coroutines.isResumeImplMethodNameFromAnyLanguageSettings
import org.jetbrains.kotlin.idea.core.util.isMultiLine
import org.jetbrains.kotlin.idea.debugger.DebuggerUtils.getMethodNameWithoutMangling
import org.jetbrains.kotlin.idea.debugger.DebuggerUtils.isGeneratedIrBackendLambdaMethodName
import org.jetbrains.kotlin.idea.debugger.isInsideInlineArgument
import org.jetbrains.kotlin.idea.debugger.safeMethod
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer

class KotlinLambdaMethodFilter(
    lambda: KtFunction,
    private val callingExpressionLines: Range<Int>?,
    private val lambdaInfo: KotlinLambdaInfo
) : BreakpointStepMethodFilter {
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
            return isInsideInlineArgument(lambda, location, process)
        }

        val method = location.safeMethod() ?: return true
        if (method.isBridge) {
            return false
        }

        val methodName = method.name() ?: return false
        return isTargetLambdaName(methodName) &&
               location.matchesExpression(process, lambda.bodyExpression)
    }

    private fun Location.matchesExpression(process: DebugProcessImpl, bodyExpression: KtExpression?): Boolean {
        val sourcePosition = process.positionManager.getSourcePosition(this) ?: return true
        val blockAt = runReadAction { sourcePosition.elementAt.parentOfType<KtBlockExpression>(true) } ?: return true
        return blockAt === bodyExpression
    }

    override fun getCallingExpressionLines() =
        if (lambdaInfo.isInline) Range(0, Int.MAX_VALUE) else callingExpressionLines

    fun isTargetLambdaName(name: String): Boolean {
        val actualName =
            if (lambdaInfo.isNameMangledInBytecode)
                name.getMethodNameWithoutMangling()
            else
                name

        if (lambdaInfo.isSuspend)
            return isResumeImplMethodNameFromAnyLanguageSettings(actualName)
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
