/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.uast.test.kotlin.org.jetbrains.uast.test.kotlin.common

import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue
import com.intellij.lang.jvm.annotation.JvmAnnotationEnumFieldValue
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import com.intellij.testFramework.assertEqualsToFile
import org.jetbrains.uast.visitor.UastVisitor
import java.io.File

// Actually this class is a duplicate of the TypesTestBase with some small change from e8f294316c5d97cf6a2209be5bc7803da49d0732
// It is done this way because it is incorrect to change the platform source in kt-* branches
// P.s. technically we could do that for test but better not to do for tests as "special case"
// Related tests: org.jetbrains.uast.test.kotlin.KotlinUastTypesTest#testAnnotatedTypes
interface TypesTestBaseDuplicate {
    fun getTypesFile(testName: String): File

    private fun UFile.asLogTypes() = TypesLogger().apply {
        this@asLogTypes.accept(this)
    }.toString()

    fun check(testName: String, file: UFile) {
        val valuesFile = getTypesFile(testName)

        assertEqualsToFile("Log values", valuesFile, file.asLogTypes())
    }

    class TypesLogger : UastVisitor {

        val builder: StringBuilder = StringBuilder()

        var level: Int = 0
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

        override fun toString(): String = builder.toString()
    }
}