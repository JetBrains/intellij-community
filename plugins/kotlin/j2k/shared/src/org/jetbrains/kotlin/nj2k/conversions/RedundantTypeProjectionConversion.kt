// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.*
import org.jetbrains.kotlin.psi.KtClass

/**
 * This processing step is analogous to the "REDUNDANT_PROJECTION" diagnostic-based postprocessing step. The goal here is to remove
 * redundant type projection keywords (i.e. `in` and `out`) by checking whether the use site and declaration have the same type projection.
 * For example, this step would change `fun <T> doThing(): T where T : Comparable<in T>` into `fun <T> doThing(): T where T : Comparable<T>`
 */
class RedundantTypeProjectionConversion(context: NewJ2kConverterContext) : RecursiveConversion(context) {
    context(KtAnalysisSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        // e.g. `out String` in `list as List<out String>`
        if (element is JKTypeElement) {
            return recurse(getNewTypeElementOrNull(element.type) ?: element)
        }

        // e.g. `T` in `class CC<T> : A() where  T : Comparable<in T>?`
        if (element is JKTypeParameter && element.upperBounds.isNotEmpty()) {
            val newUpperBounds = element.upperBounds.map { getNewTypeElementOrNull(it.type) ?: it.detached(element) }
            return recurse(JKTypeParameter(element::name.detached(), newUpperBounds, element::annotationList.detached()))
        }

        return recurse(element)
    }

    private fun getNewTypeElementOrNull(type: JKType): JKTypeElement? {
        if (type !is JKClassType || type.parameters.isEmpty()) return null
        val typeDefinition = type.classReference.target
        if (typeDefinition !is KtClass || typeDefinition.typeParameters.size != type.parameters.size) {
            return null
        }

        val newTypeParameters = type.parameters.zip(typeDefinition.typeParameters).map { (localTypeParameter, definitionTypeParameter) ->
            if (localTypeParameter is JKVarianceTypeParameterType &&
                localTypeParameter.variance.name.uppercase() == definitionTypeParameter.variance.label.uppercase()) {
                localTypeParameter.boundType as JKTypeParameterType
            } else {
                localTypeParameter
            }
        }
        return JKTypeElement(JKClassType(type.classReference, newTypeParameters, type.nullability))
    }
}
