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
package org.jetbrains.uast.test.java

import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.uast.*
import org.jetbrains.uast.test.env.findElementByText
import org.junit.Test

class JavaUastApiTest : AbstractJavaUastTest() {
    override fun check(testName: String, file: UFile) {
    }

    @Test fun testTypeReference() {
        doTest("Simple/TypeReference.java") { name, file ->
            val localVar = file.findElementByText<ULocalVariable>("String s;")
            val typeRef = localVar.typeReference
            assertNotNull(typeRef)
        }
    }

    @Test fun testFields() {
        doTest("Simple/Field.java") { name, file ->
            assertEquals(1, file.classes[0].fields.size)
        }
    }

    @Test fun testCallExpression() {
        doTest("Simple/CallExpression.java") { name, file ->
            val index = file.psi.text.indexOf("format")
            val callExpression = PsiTreeUtil.getParentOfType(file.psi.findElementAt(index), PsiCallExpression::class.java)!!
            assertNotNull(callExpression.toUElementOfType<UCallExpression>())

            val index2 = file.psi.text.indexOf("q")
            val literal = PsiTreeUtil.getParentOfType(file.psi.findElementAt(index2), PsiLiteralExpression::class.java)!!
            UsefulTestCase.assertInstanceOf(literal.toUElement(), ULiteralExpression::class.java)
        }
    }

    @Test fun testFunctionalInterfaceType() {
        doTest("Simple/FunctionalInterfaceType.java") { name, file ->
            val lambda = file.findElementByText<ULambdaExpression>("() -> { }")
            assertEquals(
                    lambda.functionalInterfaceType?.canonicalText,
                    "java.lang.Runnable")
        }
    }
}
