// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.actions.SmartStepTarget
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.idea.debugger.DebuggerUtils.getMethodNameWithoutMangling
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import java.util.*

class KotlinSmartStepTargetFilterer(
    private val targets: List<SmartStepTarget>,
    private val debugProcess: DebugProcessImpl
) {
    private val functionCounter = mutableMapOf<String, Int>()
    private val targetWasVisited = BooleanArray(targets.size) { false }

    fun visitInlineFunction(function: KtNamedFunction) {
        val descriptor = function.descriptor ?: return
        val label = KotlinMethodSmartStepTarget.calcLabel(descriptor)
        val currentCount = functionCounter.increment(label) - 1
        val matchedSteppingTargetIndex = targets.filterIsInstance<KotlinMethodSmartStepTarget>().indexOfFirst {
            it.getDeclaration() === function && it.ordinal == currentCount
        }
        if (matchedSteppingTargetIndex < 0) return
        targetWasVisited[matchedSteppingTargetIndex] = true
    }

    fun visitOrdinaryFunction(owner: String, name: String, signature: String) {
        val currentCount = functionCounter.increment("$owner.$name$signature") - 1
        for ((i, target) in targets.withIndex()) {
            if (target is KotlinMethodSmartStepTarget && target.shouldBeVisited(owner, name, signature, currentCount)) {
                targetWasVisited[i] = true
                break
            }
        }
    }

    private fun KotlinMethodSmartStepTarget.shouldBeVisited(owner: String, name: String, signature: String, currentCount: Int): Boolean {
        val actualName =
            if (methodInfo.isNameMangledInBytecode)
                name.getMethodNameWithoutMangling()
            else
                name

        if (methodInfo.isInlineClassMember) {
            return matches(
                owner,
                actualName,
                // Inline class constructor argument is injected as the first
                // argument in inline class' functions. This doesn't correspond
                // with the PSI, so we delete the first argument from the signature
                signature.getSignatureWithoutFirstArgument(),
                currentCount
            )
        }
        return matches(owner, actualName, signature, currentCount)
    }

    private fun KotlinMethodSmartStepTarget.matches(owner: String, name: String, signature: String, currentCount: Int): Boolean {
        if (methodInfo.name == name && ordinal == currentCount) {
            val lightClassMethod = getDeclaration()?.getLightClassMethod() ?: return false
            return lightClassMethod.matches(owner, name, signature, debugProcess)
        }
        return false
    }

    fun getUnvisitedTargets(): List<SmartStepTarget> =
        targets.filterIndexed { i, _ ->
            !targetWasVisited[i]
        }

    fun reset() {
        Arrays.fill(targetWasVisited, false)
        functionCounter.clear()
    }
}

private fun String.getSignatureWithoutFirstArgument() =
    removeRange(indexOf('(') + 1..indexOf(';'))

private fun KtDeclaration.getLightClassMethod(): PsiMethod? =
    when (this) {
        is KtNamedFunction    -> LightClassUtil.getLightClassMethod(this)
        is KtPropertyAccessor -> LightClassUtil.getLightClassPropertyMethods(property).getter
        else -> null
    }

private fun PsiMethod.matches(className: String, methodName: String, signature: String, debugProcess: DebugProcessImpl): Boolean =
    DebuggerUtilsEx.methodMatches(
        this,
        className.replace("/", "."),
        methodName,
        signature,
        debugProcess
    )

private fun MutableMap<String, Int>.increment(key: String): Int {
    val newValue = (get(key) ?: 0) + 1
    put(key, newValue)
    return newValue
}
