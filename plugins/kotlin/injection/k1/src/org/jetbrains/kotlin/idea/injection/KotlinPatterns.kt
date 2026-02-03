// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.injection

import com.intellij.patterns.StandardPatterns
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.base.injection.KotlinFunctionPatternBase
import org.jetbrains.kotlin.idea.base.injection.KotlinReceiverPattern
import org.jetbrains.kotlin.idea.base.injection.KtParameterPatternBase
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.resolveToParameterDescriptorIfAny
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.renderer.DescriptorRenderer

// Methods in this class are used through reflection
@Suppress("unused")
internal object KotlinPatterns : StandardPatterns() {
    @JvmStatic
    fun kotlinParameter() = KtParameterPattern()

    @JvmStatic
    fun kotlinFunction() = KotlinFunctionPattern()

    @JvmStatic
    fun receiver() = KotlinReceiverPattern()
}

// Methods in this class are used through reflection during pattern construction
@Suppress("unused")
internal class KotlinFunctionPattern : KotlinFunctionPatternBase() {
    override fun KtFunction.matchParameters(vararg parameterTypes: String): Boolean {
        val descriptor = resolveToDescriptorIfAny() as? FunctionDescriptor ?: return false
        val valueParameters = descriptor.valueParameters

        if (valueParameters.size != parameterTypes.size) return false
        for (i in 0..valueParameters.size - 1) {
            val expectedTypeString = parameterTypes[i]
            val actualParameterDescriptor = valueParameters[i]

            if (DescriptorRenderer.FQ_NAMES_IN_TYPES.renderType(actualParameterDescriptor.type) != expectedTypeString) {
                return false
            }
        }

        return true
    }

    override fun KtFunction.matchReceiver(receiverFqName: String): Boolean {
        val descriptor = resolveToDescriptorIfAny() as? FunctionDescriptor ?: return false
        val receiver = descriptor.extensionReceiverParameter ?: return false

        return DescriptorRenderer.FQ_NAMES_IN_TYPES.renderType(receiver.type) == receiverFqName
    }
}

// Methods in this class are used through reflection during pattern construction
@Suppress("unused")
internal class KtParameterPattern : KtParameterPatternBase() {
    override fun KtParameter.hasAnnotation(fqName: String): Boolean {
        val parameterDescriptor = resolveToParameterDescriptorIfAny()
        return parameterDescriptor is ValueParameterDescriptor && parameterDescriptor.annotations.any { annotation ->
            annotation.fqName?.asString() == fqName
        }
    }
}