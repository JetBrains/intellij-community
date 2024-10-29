// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.psi.*
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*

val toKotlinMutableTypesMap: Map<String, String> = mapOf(
    CommonClassNames.JAVA_UTIL_ITERATOR to StandardNames.FqNames.mutableIterator.asString(),
    CommonClassNames.JAVA_UTIL_LIST to StandardNames.FqNames.mutableList.asString(),
    CommonClassNames.JAVA_UTIL_COLLECTION to StandardNames.FqNames.mutableCollection.asString(),
    CommonClassNames.JAVA_UTIL_SET to StandardNames.FqNames.mutableSet.asString(),
    CommonClassNames.JAVA_UTIL_MAP to StandardNames.FqNames.mutableMap.asString(),
    CommonClassNames.JAVA_UTIL_MAP_ENTRY to StandardNames.FqNames.mutableMapEntry.asString(),
    java.util.ListIterator::class.java.canonicalName to StandardNames.FqNames.mutableListIterator.asString()
)

fun PsiExpression.isNullLiteral(): Boolean =
    this is PsiLiteralExpression && type == PsiTypes.nullType()

fun KtReferenceExpression.resolve(): PsiElement? =
    mainReference.resolve()

fun KtExpression.unpackedReferenceToProperty(): KtProperty? {
    val referenceExpression = when (this) {
        is KtNameReferenceExpression -> this
        is KtDotQualifiedExpression -> selectorExpression as? KtNameReferenceExpression
        else -> null
    }
    return referenceExpression?.references
        ?.firstOrNull { it is KtSimpleNameReference }
        ?.resolve() as? KtProperty
}