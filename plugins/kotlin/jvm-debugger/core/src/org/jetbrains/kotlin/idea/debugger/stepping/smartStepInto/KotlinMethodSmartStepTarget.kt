// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.psi.PsiElement
import com.intellij.util.Range
import org.jetbrains.kotlin.builtins.functions.FunctionInvokeDescriptor
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.decompiler.navigation.SourceNavigationHelper
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.renderer.ParameterNameRenderingPolicy
import org.jetbrains.kotlin.renderer.PropertyAccessorRenderingPolicy
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import javax.swing.Icon

class KotlinMethodSmartStepTarget(
    private val descriptor: CallableMemberDescriptor,
    declaration: KtDeclaration?,
    label: String,
    highlightElement: PsiElement,
    lines: Range<Int>
) : KotlinSmartStepTarget(label, highlightElement, false, lines) {
    val declaration = declaration?.let(SourceNavigationHelper::getNavigationElement)

    init {
        assert(declaration != null || isInvoke)
    }

    val isInvoke: Boolean
        get() = descriptor is FunctionInvokeDescriptor

    private val isExtension: Boolean
        get() = descriptor.isExtension

    override fun getIcon(): Icon = if (isExtension) KotlinIcons.EXTENSION_FUNCTION else KotlinIcons.FUNCTION

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

    override fun createMethodFilter() =
        KotlinMethodFilter(descriptor, callingExpressionLines, isInvoke, declaration)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        if (other == null || other !is KotlinMethodSmartStepTarget) return false

        if (isInvoke && other.isInvoke) {
            // Don't allow to choose several invoke targets in smart step into as we can't distinguish them reliably during debug
            return true
        }
        return highlightElement === other.highlightElement
    }

    override fun hashCode(): Int {
        if (isInvoke) {
            // Predefined value to make all FunctionInvokeDescriptor targets equal
            return 42
        }
        return highlightElement.hashCode()
    }
}
