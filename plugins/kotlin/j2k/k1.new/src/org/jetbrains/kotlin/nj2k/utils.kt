// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeAsciiOnly

internal inline fun <T> List<T>.mutate(mutate: MutableList<T>.() -> Unit): List<T> {
    val mutableList = toMutableList()
    mutate(mutableList)
    return mutableList
}

// Examples:
//   getMyProperty -> myProperty
//   isMyProperty -> isMyProperty
@ApiStatus.Internal
fun String.asGetterName(): String? =
    takeIf { JvmAbi.isGetterName(it) }
        ?.removePrefix("get")
        ?.takeIf {
            it.isNotEmpty() && it.first().isUpperCase()
                    || it.startsWith("is") && it.length > 2 && it[2].isUpperCase()
        }?.decapitalizeAsciiOnly()
        ?.escaped()

// Example: setMyProperty -> myProperty
@ApiStatus.Internal
fun String.asSetterName(): String? =
    takeIf { JvmAbi.isSetterName(it) }
        ?.removePrefix("set")
        ?.takeIf { it.isNotEmpty() && it.first().isUpperCase() }
        ?.decapitalizeAsciiOnly()
        ?.escaped()

internal fun String.canBeGetterOrSetterName(): Boolean =
    asGetterName() != null || asSetterName() != null

private val KEYWORDS: Set<String> = KtTokens.KEYWORDS.types.map { (it as KtKeywordToken).value }.toSet()

@ApiStatus.Internal
fun String.escaped(): String {
    val onlyUnderscores = isNotEmpty() && this.count { it == '_' } == length
    return if (this in KEYWORDS || '$' in this || onlyUnderscores) "`$this`" else this
}
