// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.engine.MethodFilter
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.Range
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.decompiler.navigation.SourceNavigationHelper
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.renderer.ParameterNameRenderingPolicy
import org.jetbrains.kotlin.renderer.PropertyAccessorRenderingPolicy
import javax.swing.Icon

class KotlinMethodSmartStepTarget(
    lines: Range<Int>,
    highlightElement: PsiElement,
    label: String,
    declaration: KtDeclaration?,
    val ordinal: Int,
    val methodInfo: CallableMemberInfo
) : KotlinSmartStepTarget(label, highlightElement, false, lines) {
    companion object {
        private val renderer = IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.withOptions {
            parameterNameRenderingPolicy = ParameterNameRenderingPolicy.NONE
            withoutReturnType = true
            propertyAccessorRenderingPolicy = PropertyAccessorRenderingPolicy.PRETTY
            startFromName = true
            modifiers = emptySet()
        }

        fun calcLabel(descriptor: DeclarationDescriptor): String {
            return renderer.render(descriptor)
        }
    }

    private val declarationPtr = declaration?.let(SourceNavigationHelper::getNavigationElement)?.createSmartPointer()

    init {
        assert(declaration != null || methodInfo.isInvoke)
    }

    override fun getIcon(): Icon = if (methodInfo.isExtension) KotlinIcons.EXTENSION_FUNCTION else KotlinIcons.FUNCTION

    fun getDeclaration(): KtDeclaration? =
        declarationPtr.getElementInReadAction()

    override fun createMethodFilter(): MethodFilter {
        val declaration = declarationPtr.getElementInReadAction()
        return KotlinMethodFilter(declaration, callingExpressionLines, methodInfo)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        if (other == null || other !is KotlinMethodSmartStepTarget) return false

        if (methodInfo.isInvoke && other.methodInfo.isInvoke) {
            // Don't allow to choose several invoke targets in smart step into as we can't distinguish them reliably during debug
            return true
        }
        return highlightElement === other.highlightElement
    }

    override fun hashCode(): Int {
        if (methodInfo.isInvoke) {
            // Predefined value to make all FunctionInvokeDescriptor targets equal
            return 42
        }
        return highlightElement.hashCode()
    }
}

internal fun <T : PsiElement> SmartPsiElementPointer<T>?.getElementInReadAction(): T? =
    this?.let { runReadAction { element } }
