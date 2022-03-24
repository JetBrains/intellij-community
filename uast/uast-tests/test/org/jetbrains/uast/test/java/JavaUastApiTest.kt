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

import com.intellij.psi.*
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor
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
    doTest("Simple/TypeReference.java") { _, file ->
      val localVar = file.findElementByText<ULocalVariable>("String s;")
      val typeRef = localVar.typeReference
      assertNotNull(typeRef)
    }
  }

  @Test
  fun testFields() {
    doTest("Simple/Field.java") { _, file ->
      assertEquals(1, file.classes[0].fields.size)
    }
  }

  @Test
  fun testPackageInfo() {
    doTest("Simple/package-info.java") { _, file ->
      val index2 = file.sourcePsi.text.indexOf("foo")
      val literal = PsiTreeUtil.getParentOfType(file.sourcePsi.findElementAt(index2), PsiLiteralExpression::class.java)!!
      val uLiteral = literal.toUElement()!!
      UsefulTestCase.assertInstanceOf(uLiteral, ULiteralExpression::class.java)
      val uAnnotation = uLiteral.getParentOfType<UAnnotation>()
      TestCase.assertNotNull(uAnnotation)
      TestCase.assertEquals("ParentPackage", (uAnnotation as UAnnotationEx).uastAnchor?.sourcePsi?.text)
    }
  }

  @Test
  fun testCallExpression() {
    doTest("Simple/CallExpression.java") { _, file ->
      val index = file.sourcePsi.text.indexOf("format")
      val callExpression = PsiTreeUtil.getParentOfType(file.sourcePsi.findElementAt(index), PsiCallExpression::class.java)!!
      assertNotNull(callExpression.toUElementOfType<UCallExpression>())

      val index2 = file.sourcePsi.text.indexOf("q")
      val literal = PsiTreeUtil.getParentOfType(file.sourcePsi.findElementAt(index2), PsiLiteralExpression::class.java)!!
      val uLiteral = literal.toUElement()!!
      UsefulTestCase.assertInstanceOf(uLiteral, ULiteralExpression::class.java)
      UsefulTestCase.assertInstanceOf(uLiteral.uastParent, UCallExpression::class.java)
      UsefulTestCase.assertInstanceOf(uLiteral.getUCallExpression(), UCallExpression::class.java)
    }
  }


  @Test
  fun testCallExpressionAlternatives() {
    doTest("Simple/CallExpression.java") { _, file ->
      val index = file.sourcePsi.text.indexOf("format")
      val callExpression = PsiTreeUtil.getParentOfType(file.sourcePsi.findElementAt(index), PsiCallExpression::class.java)!!

      val javaUastLanguagePlugin = UastLanguagePlugin.byLanguage(callExpression.language)!!

      javaUastLanguagePlugin.convertToAlternatives(callExpression, arrayOf(UCallExpression::class.java)).let {
        assertEquals("format(\"q\")", it.joinToString(transform = UExpression::asRenderString))
      }

      javaUastLanguagePlugin.convertToAlternatives<UExpression>(callExpression, arrayOf(UQualifiedReferenceExpression::class.java,
                                                                                        UCallExpression::class.java)).let {
        assertEquals("String.format(\"q\"), format(\"q\")", it.joinToString(transform = UExpression::asRenderString))
      }

      javaUastLanguagePlugin.convertToAlternatives<UExpression>(callExpression, arrayOf(UCallExpression::class.java,
                                                                                        UQualifiedReferenceExpression::class.java)).let {
        assertEquals("format(\"q\"), String.format(\"q\")", it.joinToString(transform = UExpression::asRenderString))
      }

      javaUastLanguagePlugin.convertToAlternatives(callExpression, arrayOf(UExpression::class.java)).let {
        assertEquals("String.format(\"q\"), format(\"q\")", it.joinToString(transform = UExpression::asRenderString))
      }

    }
  }

  @Test
  fun testCallExpressionArguments() {
    doTest("Simple/CallExpression.java") { _, file ->
      fun assertArguments(argumentsInPositionalOrder: List<String?>?, refText: String) =
        file.findElementByTextFromPsi<UCallExpression>(refText).let { call ->
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
    doTest("Simple/FunctionalInterfaceType.java") { _, file ->
      val lambda = file.findElementByText<ULambdaExpression>("() -> { }")
      assertEquals(
        lambda.functionalInterfaceType?.canonicalText,
        "java.lang.Runnable")
    }
  }

  @Test
  fun testReceiverType() {
    doTest("Simple/ReceiverType.java") { _, file ->
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
    doTest("Simple/SuperTypes.java") { _, file ->
      val testClass = file.findElementByTextFromPsi<UIdentifier>("Test").uastParent as UClass
      assertEquals("base class", "A", testClass.superClass?.qualifiedName)
      assertEquals("base classes", listOf("A", "B"), testClass.uastSuperTypes.map { it.getQualifiedName() })
    }
  }

  @Test
  fun testSuperTypesForAnonymous() {
    doTest("Simple/Anonymous.java") { _, file ->
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
  fun testCanFindAWayFromBrokenSwitch() = doTest("BrokenCode/Switch.java") { _, file ->
    val testClass = file.findElementByTextFromPsi<UElement>("""return;""")
    TestCase.assertEquals(7, testClass.withContainingElements.count())
  }

  @Test
  fun testDefaultConstructorRef() = doTest("Simple/ComplexCalls.java") { _, file ->
    val call = file.findElementByTextFromPsi<UCallExpression>("new C()")
    TestCase.assertEquals(UastCallKind.CONSTRUCTOR_CALL, call.kind)
    TestCase.assertEquals(null, call.resolve())
    TestCase.assertEquals("C", (call.classReference?.resolve() as? PsiClass)?.qualifiedName)
  }

  @Test
  fun testRecordParameters() {
    doTest("Simple/Record.java") { _, file ->
      val parameter = file.findElementByTextFromPsi<UElement>("int x")
      assertInstanceOf(parameter, UParameter::class.java)
      val lightParameter = (parameter as UParameter).javaPsi
      assertInstanceOf(lightParameter, PsiParameter::class.java)
      TestCase.assertEquals(parameter, lightParameter.toUElement())
      val field = file.findElementByTextFromPsi<UField>("int x")
      assertNotNull(field)
      assertInstanceOf(field.javaPsi, PsiField::class.java)
      val alternatives = UastFacade.convertToAlternatives(parameter.sourcePsi!!, DEFAULT_TYPES_LIST).toList()
      UsefulTestCase.assertSize(2, alternatives)
      TestCase.assertEquals(parameter, alternatives[0])
      TestCase.assertEquals(field, alternatives[1])
      val parameterFromField = field.javaPsi.toUElementOfType<UParameter>()
      TestCase.assertEquals(parameter, parameterFromField)
    }
  }
  
  @Test
  fun testRecordConstructor() {
    doTest("Simple/Record.java") { _, file ->
      val uClass = SyntaxTraverser.psiTraverser(file.sourcePsi).asSequence().mapNotNull { it.toUElementOfType<UClass>() }.single()
      val constructor = uClass.methods.single { it.isConstructor }
      assertInstanceOf(constructor.javaPsi, LightRecordCanonicalConstructor::class.java)
      TestCase.assertEquals(constructor, constructor.javaPsi.toUElement())
      assertInstanceOf(constructor.sourcePsi, PsiRecordHeader::class.java)
      TestCase.assertEquals(constructor, constructor.sourcePsi.toUElement())
    }
  }

}
