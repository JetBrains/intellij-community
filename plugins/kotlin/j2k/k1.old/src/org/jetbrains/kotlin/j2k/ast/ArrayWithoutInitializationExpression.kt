// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.ast

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.j2k.CodeBuilder

@K1Deprecation
class ArrayWithoutInitializationExpression(val type: ArrayType, val expressions: List<Expression>) : Expression() {
    override fun generateCode(builder: CodeBuilder) {
        fun appendConstructorName(type: ArrayType, hasInit: Boolean): CodeBuilder = when (type.elementType) {
            is PrimitiveType -> builder.append(type.toNotNullType())

            is ArrayType ->
                if (hasInit) {
                    builder.append(type.toNotNullType())
                }
                else {
                    builder.append("arrayOfNulls<").append(type.elementType).append(">")
                }

            else -> builder.append("arrayOfNulls<").append(type.elementType).append(">")
        }

        fun oneDim(type: ArrayType, size: Expression, init: (() -> Unit)? = null): CodeBuilder {
            appendConstructorName(type, init != null).append("(").append(size)
            if (init != null) {
                builder.append(", ")
                init()
            }
            return builder.append(")")
        }

        fun constructInnerType(hostType: ArrayType, expressions: List<Expression>): CodeBuilder {
            if (expressions.size == 1) {
                return oneDim(hostType, expressions[0])
            }

            val innerType = hostType.elementType
            if (expressions.size > 1 && innerType is ArrayType) {
                return oneDim(hostType, expressions[0]) {
                    builder.append("{")
                    constructInnerType(innerType, expressions.subList(1, expressions.size))
                    builder.append("}")
                }
            }

            return appendConstructorName(hostType, expressions.isNotEmpty())
        }

        constructInnerType(type, expressions)
    }
}
