// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.PositionManager
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.NamedMethodFilter
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import com.intellij.util.Range
import com.sun.jdi.LocalVariable
import com.sun.jdi.Location
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.codegen.inline.dropInlineScopeInfo
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.debugger.base.util.runDumbAnalyze
import org.jetbrains.kotlin.idea.debugger.base.util.safeLocation
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils.isGeneratedIrBackendLambdaMethodName
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils.trimIfMangledInBytecode
import org.jetbrains.kotlin.idea.debugger.core.getInlineFunctionAndArgumentVariablesToBordersMap
import org.jetbrains.kotlin.idea.debugger.core.nameMatchesUpToDollar
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypesAndPredicate

open class KotlinMethodFilter(
    element: PsiElement?,
    private val lines: Range<Int>?,
    private val methodInfo: CallableMemberInfo
) : NamedMethodFilter {
    private val elementPtr = element?.createSmartPointer()

    override fun getSkipCount(): Int = methodInfo.ordinal

    // TODO(KTIJ-23034): make Location non-null (because actually it's always non null) in next PR.
    //  This wasn't done in current PR because this it going to be cherry-picked to kt- branches, and we can't modify java debugger part.
    override fun locationMatches(process: DebugProcessImpl, location: Location?, frameProxy: StackFrameProxyImpl?): Boolean {
        if (location == null || !nameMatches(location, frameProxy)) {
            return false
        }

        return ReadAction.nonBlocking<Boolean> {
            declarationMatches(process, location)
        }.executeSynchronously()
    }

    override fun locationMatches(process: DebugProcessImpl, location: Location): Boolean {
        return locationMatches(process, location, null)
    }

    private fun declarationMatches(process: DebugProcessImpl, location: Location): Boolean {
        val currentDeclaration = getCurrentDeclaration(process.positionManager, location) ?: return false
        // Stops at first location in dumb mode
        return runDumbAnalyze(currentDeclaration, fallback = true) {
            declarationMatches(currentDeclaration)
        }
    }

    context(KaSession)
    private fun declarationMatches(currentDeclaration: KtDeclaration): Boolean {
        val currentSymbol = currentDeclaration.symbol
        // callable or constructor
        if (currentSymbol !is KaCallableSymbol && currentSymbol !is KaClassSymbol) return false
        if (methodInfo.isInvoke) {
            // There can be only one 'invoke' target at the moment so consider position as expected.
            // Descriptors can be not-equal, say when parameter has type `(T) -> T` and lambda is `Int.() -> Int`.
            return true
        }

        // Element is lost. But we know that name matches, so stop.
        val element = elementPtr?.element ?: return true
        if (element !is KtDeclaration) return false

        if (areElementsEquivalent(element, currentDeclaration)) {
            return true
        }

        if (currentSymbol !is KaCallableSymbol) return false
        for (overriddenSymbol in currentSymbol.allOverriddenSymbols) {
            val overriddenDeclaration = overriddenSymbol.psi as? KtDeclaration ?: continue
            if (areElementsEquivalent(element, overriddenDeclaration)) return true
        }

        return false
    }

    override fun getCallingExpressionLines(): Range<Int>? =
        lines

    override fun getMethodName(): String =
        methodInfo.name

    private fun nameMatches(location: Location, frameProxy: StackFrameProxyImpl?): Boolean {
        val method = location.safeMethod() ?: return false
        val targetMethodName = methodName
        val isNameMangledInBytecode = methodInfo.isNameMangledInBytecode
        val actualMethodName = method.name().trimIfMangledInBytecode(isNameMangledInBytecode)

        val isGeneratedLambda = actualMethodName.isGeneratedIrBackendLambdaMethodName()
        return actualMethodName == targetMethodName ||
                actualMethodName == "$targetMethodName${JvmAbi.DEFAULT_PARAMS_IMPL_SUFFIX}" ||
                isGeneratedLambda && getMethodNameInCallerFrame(frameProxy) == targetMethodName ||
                // A correct way here is to memorize the original location (where smart step into was started)
                // and filter out ranges that contain that original location.
                // Otherwise, nested inline with the same method name will not work correctly.
                method.getInlineFunctionAndArgumentVariablesToBordersMap()
                    .filter { location in it.value }
                    .any { it.key.isInlinedFromFunction(targetMethodName, isNameMangledInBytecode, methodInfo.isInternalMethod) } ||
                !isGeneratedLambda && methodNameMatches(methodInfo, actualMethodName)
    }
}

private fun getCurrentDeclaration(positionManager: PositionManager, location: Location): KtDeclaration? {
    val elementAt = positionManager.getSourcePosition(location)?.elementAt
    val declaration = elementAt?.getParentOfTypesAndPredicate(false, KtDeclaration::class.java) {
        it !is KtProperty || !it.isLocal
    } ?: return null
    if (declaration is KtProperty) {
        // Smart step into visitor provides accessor element as a declaration
        val currentLine = DebuggerUtilsEx.getLineNumber(location, true)
        val accessorsOnLine = declaration.accessors.filter { it.getLineNumber() == currentLine }
        if (accessorsOnLine.isNotEmpty()) {
            if (accessorsOnLine.size == 1) return accessorsOnLine.single()
            val methodName = location.safeMethod()?.name()
            if (methodName != null) {
                return if (JvmAbi.isSetterName(methodName)) {
                    accessorsOnLine.firstOrNull { it.isSetter }
                } else {
                    accessorsOnLine.firstOrNull { it.isGetter }
                }
            }
        }
    }
    return declaration
}

internal fun methodNameMatches(methodInfo: CallableMemberInfo, name: String): Boolean {
    if (methodInfo.name == name) return true
    if (methodInfo.isInternalMethod || methodInfo.isLocal) {
        return nameMatchesUpToDollar(name, methodInfo.name)
    }
    return false
}

private fun LocalVariable.isInlinedFromFunction(methodName: String, isNameMangledInBytecode: Boolean, isInternalMethod: Boolean): Boolean {
    val variableName = name().dropInlineScopeInfo().trimIfMangledInBytecode(isNameMangledInBytecode)
    if (!variableName.startsWith(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION)) return false
    val inlineMethodName = variableName.substringAfter(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION)
    return inlineMethodName == methodName ||
            isInternalMethod && nameMatchesUpToDollar(inlineMethodName, methodName)
}

private fun getMethodNameInCallerFrame(frameProxy: StackFrameProxyImpl?): String? {
    val threadProxy = frameProxy?.threadProxy() ?: return null
    val callerFrameIndex = frameProxy.frameIndex + 1
    if (callerFrameIndex >= threadProxy.frameCount()) {
        return null
    }
    val callerFrame = threadProxy.frame(callerFrameIndex)
    return callerFrame?.safeLocation()?.safeMethod()?.name()
}
