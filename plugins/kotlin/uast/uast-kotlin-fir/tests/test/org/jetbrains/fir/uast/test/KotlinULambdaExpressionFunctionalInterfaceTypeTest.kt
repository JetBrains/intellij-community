// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.fir.uast.test

import org.jetbrains.fir.uast.test.env.kotlin.AbstractFirUastTest
import org.jetbrains.uast.UFile
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.test.kotlin.TEST_KOTLIN_MODEL_PATH
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.nio.file.Path

class KotlinULambdaExpressionFunctionalInterfaceTypeTest : AbstractFirUastTest() {

    override val testBasePath: Path = TEST_KOTLIN_MODEL_PATH

    override fun check(filePath: String, file: UFile) {
        val errors = mutableListOf<String>()
        file.accept(object : AbstractUastVisitor() {
            override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
                kotlin.runCatching {
                    val samText = node.comments.firstOrNull()?.text
                        ?.removePrefix("/*")
                        ?.removeSuffix("*/")
                        ?.trim()
                        ?: fail("Could not find comment with type")
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

    fun `test ULambdaExpression functionalInterfaceType`() = doCheck("LambdaExpressionFunctionalInterfaceType.kt")
}