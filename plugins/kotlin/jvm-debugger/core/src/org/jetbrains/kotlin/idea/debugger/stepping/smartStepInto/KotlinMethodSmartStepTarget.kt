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

data class CallableMemberInfo(
    val isInvoke: Boolean,
    val isExtension: Boolean,
    val name: String
) {
    constructor(descriptor: CallableMemberDescriptor) :
            this(
                descriptor is FunctionInvokeDescriptor,
                descriptor.isExtension,
                descriptor.getMethodName()
            )
}

class KotlinMethodSmartStepTarget(
    lines: Range<Int>,
    highlightElement: PsiElement,
    label: String,
    declaration: KtDeclaration?,
    private val info: CallableMemberInfo
) : KotlinSmartStepTarget(label, highlightElement, false, lines) {
    val declaration = declaration?.let(SourceNavigationHelper::getNavigationElement)

    init {
        assert(declaration != null || info.isInvoke)
    }

    override fun getIcon(): Icon = if (info.isExtension) KotlinIcons.EXTENSION_FUNCTION else KotlinIcons.FUNCTION

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
        KotlinMethodFilter(declaration, info.isInvoke, callingExpressionLines, info.name)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        if (other == null || other !is KotlinMethodSmartStepTarget) return false

        if (info.isInvoke && other.info.isInvoke) {
            // Don't allow to choose several invoke targets in smart step into as we can't distinguish them reliably during debug
            return true
        }
        return highlightElement === other.highlightElement
    }

    override fun hashCode(): Int {
        if (info.isInvoke) {
            // Predefined value to make all FunctionInvokeDescriptor targets equal
            return 42
        }
        return highlightElement.hashCode()
    }
}
