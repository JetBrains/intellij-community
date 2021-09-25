// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.kotlin

import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiClassType
import org.jetbrains.uast.UFile
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.visitor.AbstractUastVisitor

class KotlinULambdaExpressionTest : AbstractKotlinUastTest() {
    override fun check(testName: String, file: UFile) {
        val errors = mutableListOf<String>()
        file.accept(object : AbstractUastVisitor() {
            override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
                kotlin.runCatching {
                    val classResolveResult =
                        (node.getExpressionType() as? PsiClassType)?.resolveGenerics() ?: kotlin.test.fail("cannot resolve lambda")
                    val psiMethod =
                        LambdaUtil.getFunctionalInterfaceMethod(classResolveResult.element) ?: kotlin.test.fail("cannot get method signature")
                    val methodParameters = psiMethod.getSignature(classResolveResult.substitutor).parameterTypes.toList()
                    val lambdaParameters = node.parameters.map { it.type }

                    assertEquals("parameter lists size are different", methodParameters.size, lambdaParameters.size)
                    methodParameters.zip(lambdaParameters).forEachIndexed { index, (interfaceParamType, lambdaParamType) ->
                        assertTrue(
                            "unexpected types for param $index: $lambdaParamType cannot be assigned to $interfaceParamType",
                            interfaceParamType.isAssignableFrom(lambdaParamType)
                        )
                    }
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

    fun `test lambdas parameters`() = doTest("LambdaParameters")
}