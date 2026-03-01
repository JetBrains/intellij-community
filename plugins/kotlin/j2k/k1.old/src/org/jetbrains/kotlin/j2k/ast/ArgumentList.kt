// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.ast

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.j2k.CodeBuilder
import org.jetbrains.kotlin.j2k.append

@K1Deprecation
class ArgumentList(
  val expressions: List<Expression>,
  val lPar: LPar,
  private val rPar: RPar
) : Expression() {
    override fun generateCode(builder: CodeBuilder) {
        builder.append(lPar)
        builder.append(expressions, ", ")
        builder.append(rPar)
    }

    companion object {
        fun withNoPrototype(arguments: List<Expression>): ArgumentList {
            return ArgumentList(arguments, LPar.withPrototype(null), RPar.withPrototype(null)).assignNoPrototype()
        }

        fun withNoPrototype(vararg arguments: Expression): ArgumentList = withNoPrototype(arguments.asList())
    }
}
