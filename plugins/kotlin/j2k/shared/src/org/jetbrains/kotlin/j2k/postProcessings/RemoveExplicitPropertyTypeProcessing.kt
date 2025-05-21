// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.postProcessings

import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.InspectionLikeProcessingForElement
import org.jetbrains.kotlin.nj2k.isInSingleLine
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

class RemoveExplicitPropertyTypeProcessing : InspectionLikeProcessingForElement<KtProperty>(KtProperty::class.java) {
    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun isApplicableTo(element: KtProperty, settings: ConverterSettings): Boolean {
        if (element.initializer == null) return false
        if (element.isMember && !element.isPrivate()) return false

        val typeReference = element.typeReference
        if (typeReference == null || typeReference.annotationEntries.isNotEmpty()) return false

        if (settings.specifyLocalVariableTypeByDefault && element.isLocal) return false

        allowAnalysisOnEdt {
            val initializer = element.initializer
            val newInitializer = if (element.typeReference != null && initializer != null) {
                //copy property initializer to calculate initializer's type without property's declared type
                KtPsiFactory(element.project).createExpressionCodeFragment(initializer.text, context = element).getContentElement()
            } else {
                null
            } ?: return false

            analyze(newInitializer) {
                val initializerType = newInitializer.expressionType ?: return false

                // https://kotlinlang.org/docs/coding-conventions.html#platform-types
                // Any property initialized with an expression of a platform type must declare its Kotlin type explicitly
                if (element.isMember && initializerType is KaFlexibleType) {
                    return false
                }

                val propertyType = element.symbol.returnType
                return propertyType.semanticallyEquals(initializerType)
            }
        }
    }

    override fun apply(element: KtProperty) {
        val typeReference = element.typeReference ?: return
        element.colon?.let { colon ->
            val followingWhiteSpace = colon.nextSibling?.takeIf { following ->
                following is PsiWhiteSpace && following.isInSingleLine()
            }
            followingWhiteSpace?.delete()
            colon.delete()
        }
        typeReference.delete()
    }
}