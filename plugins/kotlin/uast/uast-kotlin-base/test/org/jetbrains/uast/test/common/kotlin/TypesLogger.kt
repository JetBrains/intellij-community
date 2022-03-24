// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.common.kotlin

import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue
import com.intellij.lang.jvm.annotation.JvmAnnotationEnumFieldValue
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.visitor.UastVisitor

class TypesLogger : UastVisitor {

    val builder = StringBuilder()

    var level = 0

    override fun visitElement(node: UElement): Boolean {
        val initialLine = node.asLogString() + " [" + run {
            val renderString = node.asRenderString().lines()
            if (renderString.size == 1) {
                renderString.single()
            } else {
                renderString.first() + "..." + renderString.last()
            }
        } + "]"

        (1..level).forEach { builder.append("    ") }
        builder.append(initialLine)
        if (node is UExpression) {
            val value = node.getExpressionType()
            value?.let { psiType ->
                builder.append(" : ")
                psiType.annotations.takeIf { it.isNotEmpty() }?.joinTo(builder, ", ", "{", "}") { annotation ->
                    "@${annotation.qualifiedName}(${
                        annotation.attributes.joinToString { attr ->
                            attr.attributeName + " = " + when (val v = attr.attributeValue) {
                                is JvmAnnotationConstantValue -> v.constantValue
                                is JvmAnnotationEnumFieldValue -> v.fieldName
                                else -> v
                            }
                        }
                    })"
                }
                builder.append(psiType)
            }
        }
        builder.appendLine()
        level++
        return false
    }

    override fun afterVisitElement(node: UElement) {
        level--
    }

    override fun toString() = builder.toString()
}