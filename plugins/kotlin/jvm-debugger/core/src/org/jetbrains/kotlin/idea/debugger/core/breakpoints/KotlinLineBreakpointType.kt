// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// The package directive doesn't match the file location to prevent API breakage
package org.jetbrains.kotlin.idea.debugger.breakpoints

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.PositionManagerImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.ui.breakpoints.Breakpoint
import com.intellij.debugger.ui.breakpoints.BreakpointManager
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType
import com.intellij.debugger.ui.breakpoints.LineBreakpoint
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentsOfType
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl
import org.jetbrains.java.debugger.breakpoints.properties.JavaBreakpointProperties
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.base.psi.getTopmostElementAtOffset
import org.jetbrains.kotlin.idea.base.psi.isOneLiner
import org.jetbrains.kotlin.idea.debugger.KotlinReentrantSourcePosition
import org.jetbrains.kotlin.idea.debugger.core.KotlinDebuggerCoreBundle
import org.jetbrains.kotlin.idea.debugger.core.KotlinSourcePositionHighlighter
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.*
import org.jetbrains.kotlin.idea.debugger.getContainingMethod
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import java.util.*

interface KotlinBreakpointType

class KotlinLineBreakpointType :
    JavaLineBreakpointType("kotlin-line", KotlinDebuggerCoreBundle.message("line.breakpoint.tab.title")),
    KotlinBreakpointType
{
    inner class LineKotlinBreakpointVariant(
        position: XSourcePosition,
        element: PsiElement?,
        lambdaOrdinal: Int
    ) : LineJavaBreakpointVariant(position, element, lambdaOrdinal) {
        override fun isLowPriority(firstLineElement: PsiElement?): Boolean {
            // Dot at the beginning is the sign of a multi-line statement, lower the line breakpoint priority.
            return firstLineElement.elementType == KtTokens.DOT
        }
    }

    inner class KotlinBreakpointVariant(
        position: XSourcePosition,
        lambdaCount: Int
    ) : JavaBreakpointVariant(position, lambdaCount)

    override fun createJavaBreakpoint(
        project: Project, breakpoint:
        XBreakpoint<JavaLineBreakpointProperties>
    ): Breakpoint<JavaLineBreakpointProperties> =
        KotlinLineBreakpoint(project, breakpoint)

    override fun matchesPosition(breakpoint: LineBreakpoint<*>, position: SourcePosition): Boolean {
        val properties = breakpoint.javaBreakpointProperties
        if (properties == null || properties is JavaLineBreakpointProperties) {
            if (position is KotlinReentrantSourcePosition) {
                return false
            } else if (properties != null && (properties as JavaLineBreakpointProperties).isAllPositions) {
                return true
            }

            val containingMethod = getContainingMethod(breakpoint) ?: return false
            return inTheMethod(position, containingMethod)
        }

        return true
    }

    override fun getContainingMethod(breakpoint: LineBreakpoint<*>): PsiElement? =
        breakpoint.sourcePosition?.elementAt?.getContainingMethod()

    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean {
        return isBreakpointApplicable(file, line, project) { element: PsiElement ->
            when {
                element is KtDestructuringDeclaration ->
                    ApplicabilityResult.MAYBE_YES
                element.getContainingMethod().isInsideInlineOnlyDeclaration() ->
                    ApplicabilityResult.DEFINITELY_NO
                element.isClosingBraceInMethod() ->
                    ApplicabilityResult.MAYBE_YES
                element is KtElement -> {
                    val visitor = LineBreakpointExpressionVisitor.of(file, line)
                    if (visitor != null) {
                        element.accept(visitor, null)
                    } else {
                        ApplicabilityResult.UNKNOWN
                    }
                }
                else ->
                    ApplicabilityResult.UNKNOWN
            }
        }
    }

    override fun computeVariants(project: Project, position: XSourcePosition): List<JavaBreakpointVariant> {
        val file = PsiManager.getInstance(project).findFile(position.file) as? KtFile ?: return emptyList()

        val pos = SourcePosition.createFromLine(file, position.line)
        val allLambdas = getLambdasAtLineIfAny(pos)
        val lambdas = getLambdaBreakpointTargets(pos)
        val condRet = if (canStopOnConditionalReturn(file)) findSingleConditionalReturn(file, position.line) else null

        if (allLambdas.isEmpty() && condRet == null) return emptyList()

        val result = LinkedList<JavaLineBreakpointType.JavaBreakpointVariant>()
        val elementAt = pos.elementAt?.parentsWithSelf?.firstIsInstance<KtElement>() ?: return emptyList()
        val isLambdaElement = elementAt is KtFunction && (elementAt is KtFunctionLiteral || elementAt.name == null)
        val mainMethod = elementAt.getContainingMethod(excludingElement = isLambdaElement)
        var lineBreakpointAdded = false
        if (mainMethod != null) {
            val bodyExpression = if (mainMethod is KtDeclarationWithBody) mainMethod.bodyExpression else null
            val isLambdaResult = bodyExpression is KtLambdaExpression && bodyExpression.functionLiteral in allLambdas

            if (!isLambdaResult) {
                result.add(LineKotlinBreakpointVariant(position, mainMethod, JavaLineBreakpointProperties.NO_LAMBDA))
                lineBreakpointAdded = true
            }
        }

        lambdas.forEachIndexed { ordinal, lambda ->
            val element = lambda.bodyExpression?.takeIf { it.textLength > 0 } ?: lambda
            val positionImpl = XSourcePositionImpl.createByElement(element)
            if (positionImpl != null) {
                result.add(LambdaJavaBreakpointVariant(positionImpl, element, ordinal))
            }
        }

        if (lineBreakpointAdded && result.size > 1) {
            result.add(KotlinBreakpointVariant(position, lambdas.size))
        }

        if (condRet != null) {
            val method = condRet.getContainingMethod()
            val ordinal = lambdas.indexOf(method)
            result.add(ConditionalReturnJavaBreakpointVariant(position, condRet, ordinal))
        }

        return result
    }

    override fun getHighlightRange(breakpoint: XLineBreakpoint<JavaLineBreakpointProperties>): TextRange? {
        val position = (BreakpointManager.getJavaBreakpoint(breakpoint) as? LineBreakpoint<*>)?.sourcePosition ?: return null
        return KotlinSourcePositionHighlighter().getHighlightRange(position)
    }

    override fun getSourcePosition(breakpoint: XBreakpoint<JavaLineBreakpointProperties>): XSourcePosition? =
        calculateSourcePosition(breakpoint) ?: super.getSourcePosition(breakpoint)

    private fun calculateSourcePosition(breakpoint: XBreakpoint<JavaLineBreakpointProperties>): XSourcePosition? {
        val javaBreakpointProperties = breakpoint.properties ?: return null
        val sourcePosition = createLineSourcePosition(breakpoint as XLineBreakpointImpl<*>) ?: return null

        if (javaBreakpointProperties.isConditionalReturn) {
            runReadAction {
                findSingleConditionalReturn(sourcePosition)?.let {
                    XSourcePositionImpl.createByElement(it)
                }
            }?.let { return it }
        }

        val lambdaOrdinal = javaBreakpointProperties.lambdaOrdinal ?: return null
        val function = getLambdaByOrdinal(sourcePosition, lambdaOrdinal) ?: return null
        val firstStatement = function.bodyBlockExpression?.statements?.firstOrNull() ?: function.bodyExpression ?: return null
        return runReadAction {
            val linePosition = SourcePosition.createFromElement(firstStatement) ?: return@runReadAction null
            DebuggerUtilsEx.toXSourcePosition(
                PositionManagerImpl.JavaSourcePosition(linePosition, lambdaOrdinal)
            )
        }
    }
}

private val LineBreakpoint<*>.javaBreakpointProperties
    get() = xBreakpoint?.properties as? JavaBreakpointProperties<*>

private fun PsiElement?.isInsideInlineOnlyDeclaration(): Boolean =
    this != null && parentsOfType<KtCallableDeclaration>(withSelf = true).any { it.isInlineOnly() }

private fun PsiElement.isClosingBraceInMethod(): Boolean {
    if (this is LeafPsiElement && node.elementType === KtTokens.RBRACE) {
        val blockExpression = parent
        if (blockExpression is KtFunctionLiteral) {
            return true
        }

        if (blockExpression is KtBlockExpression) {
            val owner = blockExpression.parent
            if (owner is KtFunction || owner is KtClassInitializer) {
                return true
            }
        }
    }

    return false
}

private fun getLambdaByOrdinal(position: SourcePosition, ordinal: Int?): KtFunction? {
    if (ordinal == null || ordinal < 0) return null

    val lambdas = runReadAction {
        val targetElement = position.elementAt
        if (targetElement == null || !targetElement.isValid) {
            return@runReadAction emptyList()
        }
        getLambdaBreakpointTargets(position)
    }

    return lambdas.getOrNull(ordinal)
}

private fun getLambdaBreakpointTargets(position: SourcePosition): List<KtFunction> {
    val allLambdas = getLambdasAtLineIfAny(position)
    val line = position.line
    return allLambdas.filter { it.isSuitableLambdaBreakpointTarget(line) }
}

internal fun KtFunction.isSuitableLambdaBreakpointTarget(line: Int): Boolean =
    Registry.`is`("debugger.kotlin.multiline.lambda.breakpoints") ||
            isOneLiner() || getFirstLineWithExecutableCode() == line

/**
 * @return the first line of the lambda that contains executable code or <code>null</code> if there is no such line
 */
private fun KtFunction.getFirstLineWithExecutableCode(): Int? {
    val bodyExpression = bodyBlockExpression ?: return null
    val first = bodyExpression.firstStatement ?: return null
    return first.getLineNumber()
}

fun inTheMethod(pos: SourcePosition, method: PsiElement): Boolean {
    val elem = pos.elementAt ?: return false
    val topmostElement = getTopmostElementAtOffset(elem, elem.textRange.startOffset)
    return Comparing.equal(topmostElement.getContainingMethod(), method)
}

private fun createLineSourcePosition(breakpoint: XLineBreakpointImpl<*>): SourcePosition? {
    val file = breakpoint.file ?: return null
    val psiManager = PsiManager.getInstance(breakpoint.project)
    val psiFile = runReadAction { psiManager.findFile(file) } ?: return null
    return SourcePosition.createFromLine(psiFile, breakpoint.line)
}
