// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.NamedMethodFilter
import com.intellij.util.Range
import com.sun.jdi.LocalVariable
import com.sun.jdi.Location
import com.sun.jdi.Method
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.debugger.getInlineFunctionNamesAndBorders
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypesAndPredicate
import org.jetbrains.kotlin.resolve.DescriptorUtils

open class KotlinMethodFilter(
    declaration: KtDeclaration?,
    private val isInvoke: Boolean,
    private val lines: Range<Int>?,
    private val targetMethodName: String
) : NamedMethodFilter {
    private val declarationPtr = declaration?.createSmartPointer()

    override fun locationMatches(process: DebugProcessImpl, location: Location): Boolean {
        val method = location.method()
        val actualMethodName = method.name()

        if (!nameMatches(method, location)) {
            return false
        }

        val positionManager = process.positionManager

        val (currentDescriptor, currentDeclaration) = runReadAction {
            val elementAt = positionManager.getSourcePosition(location)?.elementAt

            val declaration = elementAt?.getParentOfTypesAndPredicate(false, KtDeclaration::class.java) {
                it !is KtProperty || !it.isLocal
            }

            if (declaration is KtClass && actualMethodName == "<init>") {
                declaration.resolveToDescriptorIfAny()?.unsubstitutedPrimaryConstructor to declaration
            } else {
                declaration?.resolveToDescriptorIfAny() to declaration
            }
        }

        if (currentDescriptor == null || currentDeclaration == null) {
            return false
        }

        @Suppress("FoldInitializerAndIfToElvis")
        if (currentDescriptor !is CallableMemberDescriptor) return false
        if (currentDescriptor.kind != CallableMemberDescriptor.Kind.DECLARATION) return false

        if (isInvoke) {
            // There can be only one 'invoke' target at the moment so consider position as expected.
            // Descriptors can be not-equal, say when parameter has type `(T) -> T` and lambda is `Int.() -> Int`.
            return true
        }

        // Element is lost. But we know that name matches, so stop.
        val declaration = runReadAction { declarationPtr?.element } ?: return true

        val psiManager = runReadAction { currentDeclaration.manager }
        if (psiManager.areElementsEquivalent(currentDeclaration, declaration)) {
            return true
        }

        return DescriptorUtils.getAllOverriddenDescriptors(currentDescriptor).any { baseOfCurrent ->
            val currentBaseDeclaration = DescriptorToSourceUtilsIde.getAnyDeclaration(currentDeclaration.project, baseOfCurrent)
            psiManager.areElementsEquivalent(declaration, currentBaseDeclaration)
        }
    }

    override fun getCallingExpressionLines() = lines

    override fun getMethodName() = targetMethodName

    private fun nameMatches(method: Method, location: Location): Boolean {
        val targetMethodName = methodName
        return method.name() == targetMethodName ||
               method.name() == "$targetMethodName${JvmAbi.DEFAULT_PARAMS_IMPL_SUFFIX}" ||
               // A correct way here is to memorize the original location (where smart step into was started)
               // and filter out ranges that contain that original location.
               // Otherwise, nested inline with the same method name will not work correctly.
               method.getInlineFunctionNamesAndBorders().filter { location in it.value }.any { it.key.isInlinedFromFunction(targetMethodName) }
    }
}

private fun LocalVariable.isInlinedFromFunction(methodName: String) =
    name().startsWith(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION) &&
    name().substringAfter(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION) == methodName
