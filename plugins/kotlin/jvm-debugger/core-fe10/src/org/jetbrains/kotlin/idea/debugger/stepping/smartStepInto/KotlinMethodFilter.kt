// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.PositionManager
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.NamedMethodFilter
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import com.intellij.util.Range
import com.sun.jdi.LocalVariable
import com.sun.jdi.Location
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.idea.debugger.base.util.safeLocation
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils.isGeneratedIrBackendLambdaMethodName
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils.trimIfMangledInBytecode
import org.jetbrains.kotlin.idea.debugger.core.getInlineFunctionAndArgumentVariablesToBordersMap
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
        analyze(currentDeclaration) {
            return declarationMatches(currentDeclaration)
        }
    }

    context(KtAnalysisSession)
    private fun declarationMatches(currentDeclaration: KtDeclaration): Boolean {
        val currentSymbol = currentDeclaration.getSymbol()
        // callable or constructor
        if (currentSymbol !is KtCallableSymbol && currentSymbol !is KtClassOrObjectSymbol) return false
        if (methodInfo.isInvoke) {
            // There can be only one 'invoke' target at the moment so consider position as expected.
            // Descriptors can be not-equal, say when parameter has type `(T) -> T` and lambda is `Int.() -> Int`.
            return true
        }

        // Element is lost. But we know that name matches, so stop.
        val element = elementPtr?.element ?: return true

        val psiManager = currentDeclaration.manager
        if (psiManager.areElementsEquivalent(currentDeclaration, element)) {
            return true
        }

        if (currentSymbol !is KtCallableSymbol) return false
        for (overriddenSymbol in currentSymbol.getAllOverriddenSymbols()) {
            val overriddenDeclaration = overriddenSymbol.psi ?: continue
            if (psiManager.areElementsEquivalent(element, overriddenDeclaration)) return true
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
                !isGeneratedLambda && methodInfo.isInternalMethod && internalNameMatches(actualMethodName, targetMethodName)
    }
}

private fun getCurrentDeclaration(positionManager: PositionManager, location: Location): KtDeclaration? {
    val elementAt = positionManager.getSourcePosition(location)?.elementAt
    return elementAt?.getParentOfTypesAndPredicate(false, KtDeclaration::class.java) {
        it !is KtProperty || !it.isLocal
    }
}

// Internal methods has a '$<MODULE_NAME>' suffix
private fun internalNameMatches(methodName: String, targetMethodName: String): Boolean {
    return methodName.startsWith("$targetMethodName\$")
}

private fun LocalVariable.isInlinedFromFunction(methodName: String, isNameMangledInBytecode: Boolean, isInternalMethod: Boolean): Boolean {
    val variableName = name().trimIfMangledInBytecode(isNameMangledInBytecode)
    if (!variableName.startsWith(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION)) return false
    val inlineMethodName = variableName.substringAfter(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION)
    return inlineMethodName == methodName ||
            isInternalMethod && internalNameMatches(inlineMethodName, methodName)
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
