// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k.ast

import org.jetbrains.kotlin.j2k.CodeBuilder
import org.jetbrains.kotlin.j2k.append

class Block(val statements: List<Statement>, val lBrace: LBrace, val rBrace: RBrace, val notEmpty: Boolean = false) : Statement() {
    override val isEmpty: Boolean
        get() = !notEmpty && statements.all { it.isEmpty }

    override fun generateCode(builder: CodeBuilder) {
        if (statements.all { it.isEmpty }) {
            if (!isEmpty) builder.append(lBrace).append(rBrace)
            return
        }

        builder.append(lBrace).append(statements, "\n", "\n", "\n").append(rBrace)
    }

    companion object {
        val Empty = Block(listOf(), LBrace(), RBrace())
        fun of(statement: Statement) = of(listOf(statement))
        fun of(statements: List<Statement>) = Block(statements, LBrace().assignNoPrototype(), RBrace().assignNoPrototype(), notEmpty = true)
    }
}

// we use LBrace and RBrace elements to better handle comments around them
class LBrace : Element() {
    override fun generateCode(builder: CodeBuilder) {
        builder.append("{")
    }
}


class RBrace : Element() {
    override fun generateCode(builder: CodeBuilder) {
        builder.append("}")
    }
}
