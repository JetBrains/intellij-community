// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.Nullability
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.toExpression
import org.jetbrains.kotlin.nj2k.tree.JKAnnotation.UseSiteTarget.PROPERTY_GETTER
import org.jetbrains.kotlin.nj2k.tree.JKAnnotationList
import org.jetbrains.kotlin.nj2k.tree.JKClass
import org.jetbrains.kotlin.nj2k.tree.JKJavaAnnotationMethod
import org.jetbrains.kotlin.nj2k.tree.JKKtAnnotationArrayInitializerExpression
import org.jetbrains.kotlin.nj2k.tree.JKKtPrimaryConstructor
import org.jetbrains.kotlin.nj2k.tree.JKModalityModifierElement
import org.jetbrains.kotlin.nj2k.tree.JKNameIdentifier
import org.jetbrains.kotlin.nj2k.tree.JKParameter
import org.jetbrains.kotlin.nj2k.tree.JKStubExpression
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement
import org.jetbrains.kotlin.nj2k.tree.JKTypeElement
import org.jetbrains.kotlin.nj2k.tree.JKVisibilityModifierElement
import org.jetbrains.kotlin.nj2k.tree.Modality
import org.jetbrains.kotlin.nj2k.tree.Visibility
import org.jetbrains.kotlin.nj2k.tree.detached
import org.jetbrains.kotlin.nj2k.tree.modality
import org.jetbrains.kotlin.nj2k.types.JKJavaArrayType
import org.jetbrains.kotlin.nj2k.types.isArrayType
import org.jetbrains.kotlin.nj2k.types.replaceJavaClassWithKotlinClassType
import org.jetbrains.kotlin.nj2k.types.updateNullabilityRecursively

class AnnotationClassConversion(context: ConverterContext) : RecursiveConversion(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKClass) return recurse(element)
        if (element.classKind != JKClass.ClassKind.ANNOTATION) return recurse(element)
        val javaAnnotationMethods =
            element.classBody.declarations
                .filterIsInstance<JKJavaAnnotationMethod>()
        val constructor = JKKtPrimaryConstructor(
            JKNameIdentifier(""),
            javaAnnotationMethods.map { it.asKotlinAnnotationParameter() },
            JKStubExpression(),
            JKAnnotationList(),
            emptyList(),
            JKVisibilityModifierElement(Visibility.PUBLIC),
            JKModalityModifierElement(Modality.FINAL)
        )
        element.modality = Modality.FINAL
        element.classBody.declarations += constructor
        element.classBody.declarations -= javaAnnotationMethods
        return recurse(element)
    }

    private fun JKJavaAnnotationMethod.asKotlinAnnotationParameter(): JKParameter {
        val type = returnType.type
            .updateNullabilityRecursively(Nullability.NotNull)
            .replaceJavaClassWithKotlinClassType(symbolProvider)
        val initializer = this::defaultValue.detached().toExpression(symbolProvider)
        val isVarArgs = type is JKJavaArrayType && name.value == "value"
        return JKParameter(
            JKTypeElement(
                if (!isVarArgs) type else (type as JKJavaArrayType).type,
                returnType::annotationList.detached()
            ),
            JKNameIdentifier(name.value),
            isVarArgs = isVarArgs,
            initializer =
                if (type.isArrayType()
                    && initializer !is JKKtAnnotationArrayInitializerExpression
                    && initializer !is JKStubExpression
                ) {
                    JKKtAnnotationArrayInitializerExpression(initializer)
                } else initializer,
            annotationList = ::annotationList.detached().also { it.annotations.forEach { ann -> ann.useSiteTarget = PROPERTY_GETTER } },
        ).also { parameter ->
            parameter.commentsBefore += commentsBefore
            parameter.commentsAfter += commentsAfter
        }
    }
}