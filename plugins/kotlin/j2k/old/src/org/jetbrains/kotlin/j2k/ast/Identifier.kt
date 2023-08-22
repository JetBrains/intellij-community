// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k.ast

import com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.kotlin.j2k.CodeBuilder
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName

fun PsiNameIdentifierOwner.declarationIdentifier(): Identifier {
    val name = name
    return if (name != null) Identifier(name, false).assignPrototype(nameIdentifier) else Identifier.Empty
}

class Identifier(
        val name: String,
        override val isNullable: Boolean = true,
        private val quotingNeeded: Boolean = true,
        private val imports: Collection<FqName> = emptyList()
) : Expression() {

    override val isEmpty: Boolean
        get() = name.isEmpty()

    private fun toKotlin(): String {
        if (quotingNeeded && KEYWORDS.contains(name) || name.contains("$")) {
            return quote(name)
        }

        return name
    }

    override fun generateCode(builder: CodeBuilder) {
        builder.append(toKotlin())

        imports.forEach { builder.addImport(it) }
    }

    private fun quote(str: String): String = "`$str`"

    override fun toString() = if (isNullable) "$name?" else name

    companion object {
        val Empty = Identifier("")

        private val KEYWORDS = KtTokens.KEYWORDS.types.map { (it as KtKeywordToken).value }.toSet()

        fun toKotlin(name: String): String = Identifier(name).toKotlin()

        fun withNoPrototype(name: String, isNullable: Boolean = true, quotingNeeded: Boolean = true, imports: Collection<FqName> = emptyList()): Identifier {
            return Identifier(name, isNullable, quotingNeeded, imports).assignNoPrototype()
        }
    }
}
