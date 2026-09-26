// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.fir.uast.test

import org.jetbrains.fir.uast.test.env.kotlin.AbstractFirUastTest
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.evaluation.uValueOf
import org.jetbrains.uast.test.kotlin.TEST_KOTLIN_MODEL_PATH
import org.jetbrains.uast.visitor.UastVisitor
import java.nio.file.Path

abstract class AbstractKotlinExpressionValueTest : AbstractFirUastTest() {

    override val testBasePath: Path = TEST_KOTLIN_MODEL_PATH

    override fun check(filePath: String, file: UFile) {
        var valuesFound = 0
        file.accept(object : UastVisitor {
            override fun visitElement(node: UElement): Boolean {
                for (comment in node.comments) {
                    val text = comment.text.removePrefix("/* ").removeSuffix(" */")
                    val parts = text.split(" = ")
                    if (parts.size != 2) continue
                    when (parts[0]) {
                        "constant" -> {
                            val expectedValue = parts[1]
                            val actualValue =
                                (node as? UExpression)?.uValueOf()?.toConstant()?.toString()
                                        ?: "cannot evaluate $node of ${node.javaClass}"
                            assertEquals(expectedValue, actualValue)
                            valuesFound++
                        }
                    }
                }
                return false
            }
        })
        assertTrue("No values found, add some /* constant = ... */ to the input file", valuesFound > 0)
    }
}