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
import junit.framework.TestCase
import org.jetbrains.uast.*
import org.jetbrains.uast.test.env.findElementByText
import org.jetbrains.uast.test.env.findElementByTextFromPsi
import org.junit.Assert
import org.junit.Test

class JavaUastApiTest : AbstractJavaUastTest() {
  override fun check(testName: String, file: UFile) {
  }

  @Test
  fun testTypeReference() {
    doTest("Simple/TypeReference.java") { name, file ->
      val localVar = file.findElementByText<ULocalVariable>("String s;")
      val typeRef = localVar.typeReference
      assertNotNull(typeRef)
    }
  }

  @Test
  fun testFields() {
    doTest("Simple/Field.java") { name, file ->
      assertEquals(1, file.classes[0].fields.size)
    }
  }

  @Test
  fun testPackageInfo() {
    doTest("Simple/package-info.java") { name, file ->
      val index2 = file.psi.text.indexOf("foo")
      val literal = PsiTreeUtil.getParentOfType(file.psi.findElementAt(index2), PsiLiteralExpression::class.java)!!
      val uLiteral = literal.toUElement()!!
      UsefulTestCase.assertInstanceOf(uLiteral, ULiteralExpression::class.java)
      val uAnnotation = uLiteral.getParentOfType<UAnnotation>()
      TestCase.assertNotNull(uAnnotation)
      TestCase.assertEquals("ParentPackage", (uAnnotation as UAnnotationEx).uastAnchor?.psi?.text)
    }
  }

  @Test
  fun testCallExpression() {
    doTest("Simple/CallExpression.java") { name, file ->
      val index = file.psi.text.indexOf("format")
      val callExpression = PsiTreeUtil.getParentOfType(file.psi.findElementAt(index), PsiCallExpression::class.java)!!
      assertNotNull(callExpression.toUElementOfType<UCallExpression>())

      val index2 = file.psi.text.indexOf("q")
      val literal = PsiTreeUtil.getParentOfType(file.psi.findElementAt(index2), PsiLiteralExpression::class.java)!!
      val uLiteral = literal.toUElement()!!
      UsefulTestCase.assertInstanceOf(uLiteral, ULiteralExpression::class.java)
      UsefulTestCase.assertInstanceOf(uLiteral.uastParent, UCallExpression::class.java)
      UsefulTestCase.assertInstanceOf(uLiteral.getUCallExpression(), UCallExpression::class.java)
    }
  }

  @Test
  fun testCallExpressionArguments() {
    doTest("Simple/CallExpression.java") { name, file ->
      fun assertArguments(argumentsInPositionalOrder: List<String?>?, refText: String) =
        file.findElementByTextFromPsi<UCallExpression>(refText).let { call ->
          if (call !is UCallExpressionEx) throw AssertionError("${call.javaClass} is not a UCallExpressionEx")
          Assert.assertEquals(
            argumentsInPositionalOrder, call.resolve()?.let { psiMethod ->
            (0 until psiMethod.parameterList.parametersCount).map {
              call.getArgumentForParameter(it)?.asRenderString()
            }
          }
          )
        }
      assertArguments(listOf("\"q\"", "varargs "), "String.format(\"q\")")
      assertArguments(listOf("\"%d %s\"", "varargs 1 : \"asd\""), "String.format(\"%d %s\", 1, \"asd\")")
      assertArguments(listOf("\"%s\"", "varargs String(\"a\", \"b\", \"c\") as java.lang.Object[]"),
                      "String.format(\"%s\", (Object[])new String[]{\"a\", \"b\", \"c\"})")
      assertArguments(listOf("\"%s\"", "varargs String(\"d\", \"e\", \"f\")"), "String.format(\"%s\", new String[]{\"d\", \"e\", \"f\"})")

    }
  }

  @Test
  fun testFunctionalInterfaceType() {
    doTest("Simple/FunctionalInterfaceType.java") { name, file ->
      val lambda = file.findElementByText<ULambdaExpression>("() -> { }")
      assertEquals(
        lambda.functionalInterfaceType?.canonicalText,
        "java.lang.Runnable")
    }
  }

  @Test
  fun testReceiverType() {
    doTest("Simple/ReceiverType.java") { name, file ->
      assertEquals("Test", file.findElementByText<UCallExpression>("foo(1)").receiverType?.canonicalText)
      assertEquals("Test", file.findElementByText<UCallExpression>("fooBase(1)").receiverType?.canonicalText)
      assertEquals("Test", file.findElementByText<UCallExpression>("this.barBase(1)").receiverType?.canonicalText)
      assertEquals("Test", file.findElementByText<UCallExpression>("bazBaseBase(1)").receiverType?.canonicalText)
      assertNull(file.findElementByText<UCallExpression>("staticMethod(1)").receiverType)

      assertEquals("Test", file.findElementByText<UCallExpression>("foo(2)").receiverType?.canonicalText)
      assertEquals("Test", file.findElementByText<UCallExpression>("fooBase(2)").receiverType?.canonicalText)
      assertEquals("Test.InnerTest", file.findElementByText<UCallExpression>("bar(2)").receiverType?.canonicalText)
      assertNull(file.findElementByText<UCallExpression>("staticMethod(2)").receiverType)

      assertEquals("Test", file.findElementByText<UCallExpression>("foo(3)").receiverType?.canonicalText)
      assertEquals("Test", file.findElementByText<UCallExpression>("fooBase(3)").receiverType?.canonicalText)
      assertEquals("Test.InnerTest", file.findElementByText<UCallExpression>("bar(3)").receiverType?.canonicalText)
      assertEquals("Test.InnerTest.InnerInnerTest", file.findElementByText<UCallExpression>("baz(3)").receiverType?.canonicalText)
      assertNull(file.findElementByText<UCallExpression>("staticMethod(3)").receiverType)

      assertEquals("Test", file.findElementByText<UCallExpression>("foo(4)").receiverType?.canonicalText)
      assertEquals("Test.InnerTest2", file.findElementByText<UCallExpression>("fooBase(4)").receiverType?.canonicalText)

      assertEquals("Test.StaticTest", file.findElementByText<UCallExpression>("bar(5)").receiverType?.canonicalText)
      assertNull(file.findElementByText<UCallExpression>("staticMethod(5)").receiverType)

      assertEquals("Test.StaticTest", file.findElementByText<UCallExpression>("bar(6)").receiverType?.canonicalText)

      assertEquals("TestBase", file.findElementByText<UCallExpression>("bazBaseBase(7)").receiverType?.canonicalText)
      assertEquals("Test.InnerTest", file.findElementByText<UCallExpression>("bar(7)").receiverType?.canonicalText)
      assertEquals("TestBase", file.findElementByText<UCallExpression>("barBase(7)").receiverType?.canonicalText)
    }
  }

  @Test
  fun testSuperTypes() {
    doTest("Simple/SuperTypes.java") { name, file ->
      val testClass = file.findElementByTextFromPsi<UIdentifier>("Test").uastParent as UClass
      assertEquals("base class", "A", testClass.superClass?.qualifiedName)
      assertEquals("base classes", listOf("A", "B"), testClass.uastSuperTypes.map { it.getQualifiedName() })
    }
  }

  @Test
  fun testSuperTypesForAnonymous() {
    doTest("Simple/Anonymous.java") { name, file ->
      val testClass = file.findElementByTextFromPsi<UElement>("""Runnable() {

            public void run() {
                int variable = 24;
                variable++;
            }
        }""") as UAnonymousClass
      assertEquals("base classes", listOf("java.lang.Runnable"), testClass.uastSuperTypes.map { it.getQualifiedName() })
    }
  }

  @Test
  fun testCanFindAWayFromBrokenSwitch() = doTest("BrokenCode/Switch.java") { name, file ->
    val testClass = file.findElementByTextFromPsi<UElement>("""return;""")
    TestCase.assertEquals(7, testClass.withContainingElements.count())
  }

}
