// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.forEachDescendantOfType
import org.jetbrains.kotlin.nj2k.symbols.JKUniverseFieldSymbol
import org.jetbrains.kotlin.nj2k.tree.JKClassBody
import org.jetbrains.kotlin.nj2k.tree.JKExpression
import org.jetbrains.kotlin.nj2k.tree.JKField
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement
import org.jetbrains.kotlin.nj2k.unboxFieldReference

internal fun findAllUsagesOfFieldsIn(scope: JKTreeElement, owningClass: JKClassBody, filter: (JKField) -> Boolean): Collection<JKField> {
    val result = mutableSetOf<JKField>()
    scope.forEachDescendantOfType<JKExpression> { expression ->
        val symbol = expression.unboxFieldReference()?.identifier as? JKUniverseFieldSymbol ?: return@forEachDescendantOfType
        val field = symbol.target as? JKField ?: return@forEachDescendantOfType
        if (!filter(field)) return@forEachDescendantOfType
        if (field.parent != owningClass) return@forEachDescendantOfType
        result.add(field)
    }
    return result
}