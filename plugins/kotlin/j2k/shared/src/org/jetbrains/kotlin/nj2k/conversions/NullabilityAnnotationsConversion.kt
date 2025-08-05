// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.Nullability
import org.jetbrains.kotlin.load.java.NOT_NULL_ANNOTATIONS
import org.jetbrains.kotlin.load.java.NULLABLE_ANNOTATIONS
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.updateNullability

class NullabilityAnnotationsConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKAnnotationListOwner) return recurse(element)

        val annotationsToRemove = mutableListOf<JKAnnotation>()
        for (annotation in element.annotationList.annotations) {
            val nullability = annotation.annotationNullability() ?: continue
            when (element) {
                is JKVariable -> element.type
                is JKMethod -> element.returnType
                is JKTypeElement -> element
                else -> null
            }?.let { typeElement ->
                annotationsToRemove += annotation
                typeElement.type = typeElement.type.updateNullability(nullability)
            }
        }
        element.annotationList.annotations -= annotationsToRemove

        return recurse(element)
    }

    private fun JKAnnotation.annotationNullability(): Nullability? =
        when (classSymbol.fqName) {
            in nullableAnnotationsFqNames -> Nullability.Nullable
            in notNullAnnotationsFqNames -> Nullability.NotNull
            else -> null
        }

    companion object {
        private val nullableAnnotationsFqNames =
            NULLABLE_ANNOTATIONS.map { it.asString() }.toSet()
        private val notNullAnnotationsFqNames =
            NOT_NULL_ANNOTATIONS.map { it.asString() }.toSet()
    }
}