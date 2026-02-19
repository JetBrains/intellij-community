// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.OutputValue.ExpressionValue
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.OutputValue.Initializer
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

enum class ExtractionTarget(val targetName: String) {
    FUNCTION(KotlinBundle.message("text.function")) {
        override fun isAvailable(descriptor: IExtractableCodeDescriptor<*>) = true
    },

    FAKE_LAMBDALIKE_FUNCTION(KotlinBundle.message("text.lambda.parameter")) {
        override fun isAvailable(descriptor: IExtractableCodeDescriptor<*>): Boolean {
            return checkSimpleControlFlow(descriptor) || descriptor.controlFlow.outputValues.isEmpty()
        }
    },

    PROPERTY_WITH_INITIALIZER(KotlinBundle.message("text.property.with.initializer")) {
        override fun isAvailable(descriptor: IExtractableCodeDescriptor<*>): Boolean {
            return checkSignatureAndParent(descriptor)
                    && checkSimpleControlFlow(descriptor)
                    && checkSimpleBody(descriptor)
                    && checkNotInterface(descriptor)
                    && descriptor.receiverParameter == null
        }
    },

    PROPERTY_WITH_GETTER(KotlinBundle.message("text.property.with.getter")) {
        override fun isAvailable(descriptor: IExtractableCodeDescriptor<*>): Boolean {
            return checkSignatureAndParent(descriptor)
        }
    },

    LAZY_PROPERTY(KotlinBundle.message("text.lazy.property")) {
        override fun isAvailable(descriptor: IExtractableCodeDescriptor<*>): Boolean {
            return checkSignatureAndParent(descriptor)
                    && checkSimpleControlFlow(descriptor)
                    && checkNotInterface(descriptor)
                    && descriptor.receiverParameter == null
        }
    };

    abstract fun isAvailable(descriptor: IExtractableCodeDescriptor<*>): Boolean

    companion object {
        fun checkNotInterface(descriptor: IExtractableCodeDescriptor<*>): Boolean {
            val parent = descriptor.extractionData.targetSibling.getStrictParentOfType<KtDeclaration>()
            return !(parent is KtClass && parent.isInterface())
        }

        fun checkSimpleBody(descriptor: IExtractableCodeDescriptor<*>): Boolean {
            val expression = descriptor.extractionData.expressions.singleOrNull()
            return expression != null && expression !is KtDeclaration && expression !is KtBlockExpression
        }

        fun checkSimpleControlFlow(descriptor: IExtractableCodeDescriptor<*>): Boolean {
            val outputValue = descriptor.controlFlow.outputValues.singleOrNull()
            return (outputValue is ExpressionValue && !outputValue.callSiteReturn) || outputValue is Initializer
        }

        fun checkSignatureAndParent(descriptor: IExtractableCodeDescriptor<*>): Boolean {
            if (!descriptor.parameters.isEmpty()) return false
            if (descriptor.isUnitReturnType()) return false

            val parent = descriptor.extractionData.targetSibling.parent
            return (parent is KtFile || parent is KtClassBody)
        }
    }
}


val propertyTargets: List<ExtractionTarget> = listOf(
    ExtractionTarget.PROPERTY_WITH_INITIALIZER,
    ExtractionTarget.PROPERTY_WITH_GETTER,
    ExtractionTarget.LAZY_PROPERTY
)