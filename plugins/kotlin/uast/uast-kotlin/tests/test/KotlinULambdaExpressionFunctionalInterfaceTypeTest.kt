// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.kotlin

import org.jetbrains.uast.UFile
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.visitor.AbstractUastVisitor

class KotlinULambdaExpressionFunctionalInterfaceTypeTest : AbstractKotlinUastTest() {
    override fun check(testName: String, file: UFile) {
        val errors = mutableListOf<String>()
        file.accept(object : AbstractUastVisitor() {
            override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
                kotlin.runCatching {
                    val samText = node.comments.firstOrNull()?.text
                        ?.removePrefix("/*")
                        ?.removeSuffix("*/")
                        ?.trim()
                        ?: kotlin.test.fail("Could not find comment with type")
                    val actualSam = node.functionalInterfaceType?.canonicalText

                    assertEquals("Unexpected canonical text", samText, actualSam)
                }.onFailure {
                    errors += "${node.getContainingUMethod()?.name}: ${it.message}"
                }

                return super.visitLambdaExpression(node)
            }
        })

        assertTrue(
            errors.joinToString(separator = "\n", postfix = "", prefix = "") { it },
            errors.isEmpty()
        )
    }

    fun `test ULambdaExpression functionalInterfaceType`() = doTest("LambdaExpressionFunctionalInterfaceType")
}