// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.j2k.Nullability.NotNull
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.tree.JKParenthesizedExpression
import org.jetbrains.kotlin.nj2k.tree.JKQualifiedExpression
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement
import org.jetbrains.kotlin.nj2k.tree.JKTypeCastExpression
import org.jetbrains.kotlin.nj2k.types.updateNullability

/**
 * Try to determine more precise nullability for some JK elements.
 * See also [org.jetbrains.kotlin.nj2k.JavaToJKTreeBuilder.collectNullabilityInfo]
 */
class NullabilityConversion(context: NewJ2kConverterContext) : RecursiveConversion(context) {
    context(KtAnalysisSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element is JKTypeCastExpression) {
            element.updateNullability()
        }

        return recurse(element)
    }

    private fun JKTypeCastExpression.updateNullability() {
        val qualifiedExpression = (parent as? JKParenthesizedExpression)?.parent as? JKQualifiedExpression
        if (qualifiedExpression != null) {
            // In code such as `((String o)).length()`, the cast's type can be considered not-null
            // (it is equivalent to Kotlin's unsafe cast)
            type.type = type.type.updateNullability(NotNull)
        }
    }
}
