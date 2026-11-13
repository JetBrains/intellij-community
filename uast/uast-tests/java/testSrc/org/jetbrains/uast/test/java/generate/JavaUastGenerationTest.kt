// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java.generate

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiBinaryExpression
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiPolyadicExpression
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.generate.UParameterInfo
import org.jetbrains.uast.generate.UastCodeGenerationPlugin
import org.jetbrains.uast.generate.refreshed
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.toUElementOfType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

@TestApplication
class JavaUastGenerationTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  private val psiFactory: PsiElementFactory
    get() = JavaPsiFacade.getElementFactory(myFixture.project)
  private val generatePlugin: UastCodeGenerationPlugin
    get() = UastCodeGenerationPlugin.byLanguage(JavaLanguage.INSTANCE)!!
  private val uastElementFactory
    get() = generatePlugin.getElementFactory(myFixture.project)

  @Test
  fun `test logical and operation with simple operands`() {
    ReadAction.runBlocking<RuntimeException> {
      val left = psiFactory.createExpressionFromText("true", null).toUElementOfType<UExpression>()
                 ?: fail("Cannot create left UExpression")
      val right = psiFactory.createExpressionFromText("false", null).toUElementOfType<UExpression>()
                  ?: fail("Cannot create right UExpression")

      val expression = uastElementFactory.createBinaryExpression(left, right, UastBinaryOperator.LOGICAL_AND, null)
                       ?: fail("Cannot create expression")

      assertEquals("true && false", expression.sourcePsi?.text)
    }
  }

  @Test
  fun `test logical and operation with simple operands with parenthesis`() {
    ReadAction.runBlocking<RuntimeException> {
      val left = psiFactory.createExpressionFromText("(true)", null).toUElementOfType<UExpression>()
                 ?: fail("Cannot create left UExpression")
      val right = psiFactory.createExpressionFromText("(false)", null).toUElementOfType<UExpression>()
                  ?: fail("Cannot create right UExpression")

      val expression = uastElementFactory.createFlatBinaryExpression(left, right, UastBinaryOperator.LOGICAL_AND, null)
                       ?: fail("Cannot create expression")

      assertTrue(expression.sourcePsi is PsiBinaryExpression)
      assertEquals("true && false", expression.sourcePsi?.text)
    }
  }

  @Test
  fun `test logical and operation with simple operands with parenthesis polyadic`() {
    ReadAction.runBlocking<RuntimeException> {
      val left = psiFactory.createExpressionFromText("(true && false)", null).toUElementOfType<UExpression>()
                 ?: fail("Cannot create left UExpression")
      val right = psiFactory.createExpressionFromText("(false)", null).toUElementOfType<UExpression>()
                  ?: fail("Cannot create right UExpression")

      val expression = uastElementFactory.createFlatBinaryExpression(left, right, UastBinaryOperator.LOGICAL_AND, null)
                       ?: fail("Cannot create expression")

      assertTrue(expression.sourcePsi is PsiPolyadicExpression)
      assertEquals("true && false && false", expression.sourcePsi?.text)
    }
  }

  @Test
  fun `test simple reference creating from variable`() {
    ReadAction.runBlocking<RuntimeException> {
      val variable = psiFactory.createVariableDeclarationStatement(
        "a",
        PsiTypes.intType(), null
      ).declaredElements.getOrNull(0)?.toUElementOfType<UVariable>() ?: fail("cannot create variable")

      val reference = uastElementFactory.createSimpleReference(variable, null) ?: fail("cannot create reference")
      assertEquals("a", reference.identifier)
    }
  }

  @Test
  fun `test simple reference by name`() {
    ReadAction.runBlocking<RuntimeException> {
      val reference = uastElementFactory.createSimpleReference("a", null) ?: fail("cannot create reference")
      assertEquals("a", reference.identifier)
    }
  }

  @Test
  fun `test parenthesised expression`() {
    ReadAction.runBlocking<RuntimeException> {
      val expression = psiFactory.createExpressionFromText("a + b", null).toUElementOfType<UExpression>()
                       ?: fail("cannot create expression")
      val parenthesizedExpression = uastElementFactory.createParenthesizedExpression(expression, null)
                                    ?: fail("cannot create parenthesized expression")

      assertEquals("(a + b)", parenthesizedExpression.sourcePsi?.text)
    }
  }

  @Test
  fun `test return expression`() {
    ReadAction.runBlocking<RuntimeException> {
      val expression = psiFactory.createExpressionFromText("a + b", null).toUElementOfType<UExpression>()
                       ?: fail("Cannot find plugin")

      val returnExpression = uastElementFactory.createReturnExpression(expression, false, null) ?: fail("cannot create return expression")

      assertEquals("return a + b;", returnExpression.sourcePsi?.text)
    }
  }

  @Test
  fun `test variable declaration without type`() {
    ReadAction.runBlocking<RuntimeException> {
      val expression = psiFactory.createExpressionFromText("1 + 2", null).toUElementOfType<UExpression>()
                       ?: fail("cannot create variable declaration")

      val declaration = uastElementFactory.createLocalVariable("a", null, expression, false, null) ?: fail("cannot create variable")

      assertEquals("int a = 1 + 2;", declaration.sourcePsi?.text)
    }
  }

  @Test
  fun `test variable declaration with type`() {
    ReadAction.runBlocking<RuntimeException> {
      val expression = psiFactory.createExpressionFromText("b", null).toUElementOfType<UExpression>()
                       ?: fail("cannot create variable declaration")

      val declaration = uastElementFactory.createLocalVariable("a", PsiTypes.doubleType(), expression, false, null) ?: fail("cannot create variable")

      assertEquals("double a = b;", declaration.sourcePsi?.text)
    }
  }

  @Test
  fun `test final variable declaration`() {
    ReadAction.runBlocking<RuntimeException> {
      val expression = psiFactory.createExpressionFromText("b", null).toUElementOfType<UExpression>()
                       ?: fail("cannot create variable declaration")

      val declaration = uastElementFactory.createLocalVariable("a", PsiTypes.doubleType(), expression, true, null)
                        ?: fail("cannot create variable")

      assertEquals("final double a = b;", declaration.sourcePsi?.text)
    }
  }

  @Test
  fun `test final variable declaration with unique name`() {
    ReadAction.runBlocking<RuntimeException> {
      val context = psiFactory.createVariableDeclarationStatement("a", PsiTypes.intType(), null, null)
      val expression = psiFactory.createExpressionFromText("b", context).toUElementOfType<UExpression>()
                       ?: fail("cannot create variable declaration")

      val declaration = uastElementFactory.createLocalVariable("a", PsiTypes.doubleType(), expression, true, null)
                        ?: fail("cannot create variable")

      assertEquals("final double a1 = b;", declaration.sourcePsi?.text)
    }
  }

  @Test
  fun `test block expression`() {
    ReadAction.runBlocking<RuntimeException> {
      val statement1 = psiFactory.createStatementFromText("System.out.println();", null).toUElementOfType<UExpression>()
                       ?: fail("cannot create statement")
      val statement2 = psiFactory.createStatementFromText("System.out.println(2);", null).toUElementOfType<UExpression>()
                       ?: fail("cannot create statement")

      val block = uastElementFactory.createBlockExpression(listOf(statement1, statement2), null) ?: fail("cannot create block")

      assertEquals("{" +
                            "System.out.println();" +
                            "System.out.println(2);" +
                            "}", block.sourcePsi?.text)
    }
  }

  @Test
  fun `test lambda expression`() {
    ReadAction.runBlocking<RuntimeException> {
      val statement = psiFactory.createStatementFromText("System.out.println();", null).toUElementOfType<UExpression>()
                      ?: fail("cannot create statement")

      val lambda = uastElementFactory.createLambdaExpression(
        listOf(
          UParameterInfo(PsiTypes.intType(), "a"),
          UParameterInfo(null, "b")
        ),
        statement,
        null) ?: fail("cannot create lambda")

      assertEquals("(a,b)->System.out.println()", lambda.sourcePsi?.text)
    }
  }

  @Test
  fun `test lambda expression with explicit types`() {
    ReadAction.runBlocking<RuntimeException> {
      val statement = psiFactory.createStatementFromText("System.out.println();", null).toUElementOfType<UExpression>()
                      ?: fail("cannot create statement")

      val lambda = uastElementFactory.createLambdaExpression(
        listOf(
          UParameterInfo(PsiTypes.intType(), "a"),
          UParameterInfo(PsiTypes.doubleType(), "b")
        ),
        statement,
        null) ?: fail("cannot create lambda")

      assertEquals("(int a,double b)->System.out.println()", lambda.sourcePsi?.text)
    }
  }

  @Test
  fun `test lambda expression with simplified block body`() {
    ReadAction.runBlocking<RuntimeException> {
      val block = psiFactory.createStatementFromText("{ return \"10\"; }", null).toUElementOfType<UBlockExpression>()
                  ?: fail("cannot create block")

      val lambda = uastElementFactory.createLambdaExpression(listOf(UParameterInfo(null, "a")), block, null)
                   ?: fail("cannot create lambda")
      assertEquals("""a->"10"""", lambda.sourcePsi?.text)
    }
  }

  @Test
  fun `test lambda expression with simplified block body with context`() {
    ReadAction.runBlocking<RuntimeException> {
      val context = psiFactory.createVariableDeclarationStatement("a", PsiTypes.intType(), null)
      val block = psiFactory.createStatementFromText("{ return \"10\"; }", context).toUElementOfType<UBlockExpression>()
                  ?: fail("cannot create block")

      val lambda = uastElementFactory.createLambdaExpression(listOf(UParameterInfo(null, "a")), block, null)
                   ?: fail("cannot create lambda")
      assertEquals("""a1->"10"""", lambda.sourcePsi?.text)
    }
  }

  @Test
  fun `test function argument replacement`() {
    ReadAction.runBlocking<RuntimeException> {
      val expression = psiFactory.createExpressionFromText("f(a)", null).toUElementOfType<UCallExpression>()
                       ?: fail("cannot create expression")
      val newArgument = psiFactory.createExpressionFromText("b", null).toUElementOfType<USimpleNameReferenceExpression>()
                        ?: fail("cannot create reference")

      assertNotNull(expression.valueArguments[0].replace(newArgument))
      val updated = expression.refreshed() ?: fail("cannot update expression")
      assertEquals("f", updated.methodName)
      assertTrue(updated.valueArguments[0] is USimpleNameReferenceExpression)
      assertEquals("b", (updated.valueArguments[0] as USimpleNameReferenceExpression).identifier)
    }
  }

  @Test
  fun `test suggested name`() {
    ReadAction.runBlocking<RuntimeException> {
      val expression = psiFactory.createExpressionFromText("f(a) + 1", null).toUElementOfType<UExpression>()
                       ?: fail("cannot create expression")
      val variable = uastElementFactory.createLocalVariable(null, PsiTypes.intType(), expression, true, null)
                     ?: fail("cannot create variable")

      assertEquals("final int i = f(a) + 1;", variable.sourcePsi?.text)
    }
  }

  @Test
  fun `test method call generation with receiver`() {
    ReadAction.runBlocking<RuntimeException> {
      val receiver = psiFactory.createExpressionFromText(""""10"""", null).toUElementOfType<UExpression>()
                     ?: fail("cannot create receiver")
      val arg1 = psiFactory.createExpressionFromText("1", null).toUElementOfType<UExpression>()
                 ?: fail("cannot create arg1")
      val arg2 = psiFactory.createExpressionFromText("2", null).toUElementOfType<UExpression>()
                 ?: fail("cannot create arg2")
      val methodCall = uastElementFactory.createCallExpression(
        receiver,
        "substring",
        listOf(arg1, arg2),
        null,
        UastCallKind.METHOD_CALL) ?: fail("cannot create call")

      assertEquals(""""10".substring(1, 2)""", methodCall.sourcePsi?.text)
    }
  }

  @Test
  fun `test method call generation without receiver`() {
    ReadAction.runBlocking<RuntimeException> {
      val arg1 = psiFactory.createExpressionFromText("1", null).toUElementOfType<UExpression>()
                 ?: fail("cannot create arg1")
      val arg2 = psiFactory.createExpressionFromText("2", null).toUElementOfType<UExpression>()
                 ?: fail("cannot create arg2")
      val methodCall = uastElementFactory.createCallExpression(
        null,
        "substring",
        listOf(arg1, arg2),
        null,
        UastCallKind.METHOD_CALL) ?: fail("cannot create call")

      assertEquals("""substring(1, 2)""", methodCall.sourcePsi?.text)
    }
  }

  @Test
  fun `test method call generation with generics restoring`() {
    ReadAction.runBlocking<RuntimeException> {
      val arrays = psiFactory.createExpressionFromText("java.util.Arrays", null).toUElementOfType<UExpression>()
                   ?: fail("cannot create receiver")
      val methodCall = uastElementFactory.createCallExpression(
        arrays,
        "asList",
        listOf(),
        psiFactory.createTypeFromText("java.util.List<java.lang.String>", null),
        UastCallKind.METHOD_CALL
      ) ?: fail("cannot create call")
      assertEquals("java.util.Arrays.<String>asList()", methodCall.sourcePsi?.text)
    }
  }

  @Test
  fun `test method call generation with generics restoring 2 parameters`() {
    ReadAction.runBlocking<RuntimeException> {
      val collections = psiFactory.createExpressionFromText("java.util.Collections", null).toUElementOfType<UExpression>()
                        ?: fail("cannot create receiver")
      val methodCall = uastElementFactory.createCallExpression(
        collections,
        "emptyMap",
        listOf(),
        psiFactory.createTypeFromText(
          "java.util.Map<java.lang.String, java.lang.Integer>",
          null
        ),
        UastCallKind.METHOD_CALL
      ) ?: fail("cannot create call")
      assertEquals("java.util.Collections.<String, Integer>emptyMap()", methodCall.sourcePsi?.text)
    }
  }

  @Test
  fun `test method call generation with generics restoring 1 parameter with 1 existing`() {
    val newClass = myFixture.addClass("""
      class A {
        public static <T1, T2> java.util.Map<T1, T2> kek(T2 a) {
          return null;
        }
      }
    """.trimIndent())
    ReadAction.runBlocking<RuntimeException> {
      val a = psiFactory.createExpressionFromText("A", newClass).toUElementOfType<UExpression>()
              ?: fail("cannot create a receiver")
      val param = psiFactory.createExpressionFromText("\"a\"", null).toUElementOfType<UExpression>()
                  ?: fail("cannot create a parameter")
      val methodCall = uastElementFactory.createCallExpression(
        a,
        "kek",
        listOf(param),
        psiFactory.createTypeFromText(
          "java.util.Map<java.lang.String, java.lang.Integer>",
          null
        ),
        UastCallKind.METHOD_CALL
      ) ?: fail("cannot create call")

      assertEquals("A.<String, Integer>kek(\"a\")", methodCall.sourcePsi?.text)
    }
  }

  @Test
  fun `test method call generation with generics restoring 1 parameter with 1 unused `() {
    val newClass = myFixture.addClass("""
      class A {
        public static <T1, T2, T3> java.util.Map<T1, T3> kek(T1 a) {
          return null;
        }
      }
    """.trimIndent())
    ReadAction.runBlocking<RuntimeException> {
      val a = psiFactory.createExpressionFromText("A", newClass).toUElementOfType<UExpression>()
              ?: fail("cannot create a receiver")
      val param = psiFactory.createExpressionFromText("\"a\"", null).toUElementOfType<UExpression>()
                  ?: fail("cannot create a parameter")
      val methodCall = uastElementFactory.createCallExpression(
        a,
        "kek",
        listOf(param),
        psiFactory.createTypeFromText(
          "java.util.Map<java.lang.String, java.lang.Integer>",
          null
        ),
        UastCallKind.METHOD_CALL
      ) ?: fail("cannot create call")

      assertEquals("A.<String, Object, Integer>kek(\"a\")", methodCall.sourcePsi?.text)
    }
  }

  @Test
  fun `test method call generation with generics with context`() {
    val newClass = myFixture.addClass("""
      class A {
        public <T> java.util.List<T> method();
      }
    """.trimIndent())

    ReadAction.runBlocking<RuntimeException> {
      val declaration = psiFactory.createStatementFromText("A a = new A();", newClass)
      val reference = psiFactory.createExpressionFromText("a", declaration)
                        .toUElementOfType<UReferenceExpression>() ?: fail("cannot create reference expression")
      val callExpression = uastElementFactory.createCallExpression(
        reference,
        "method",
        emptyList(),
        psiFactory.createTypeFromText(
          "java.util.List<java.lang.Integer>",
          null
        ),
        UastCallKind.METHOD_CALL,
        context = reference.sourcePsi
      ) ?: fail("cannot create method call")

      assertEquals("a.<Integer>method()", callExpression.sourcePsi?.text)
    }
  }

  @Test
  fun `test callable reference generation with receiver`() {
    ReadAction.runBlocking<RuntimeException> {
      val receiver = uastElementFactory.createQualifiedReference("java.util.Arrays", myFixture.file)
                     ?: fail("failed to create receiver")
      val methodReference = uastElementFactory.createCallableReferenceExpression(receiver, "asList", myFixture.file)
                            ?: fail("failed to create method reference")
      assertEquals(methodReference.sourcePsi?.text, "java.util.Arrays::asList")
    }
  }

  @Test
  fun `test removing unnecessary type parameters while replace`() {
    val newClass = myFixture.addClass("""
      class A {
        public <T> java.util.List<T> method();
      }
    """.trimIndent())

    ReadAction.runBlocking<RuntimeException> {
      val declaration = psiFactory.createStatementFromText("A a = new A();", newClass)
      val reference = psiFactory.createExpressionFromText("a", declaration)
                        .toUElementOfType<UReferenceExpression>() ?: fail("cannot create reference expression")
      val callExpression = uastElementFactory.createCallExpression(
        reference,
        "method",
        emptyList(),
        psiFactory.createTypeFromText(
          "java.util.List<java.lang.Integer>",
          null
        ),
        UastCallKind.METHOD_CALL,
        context = reference.sourcePsi
      ) ?: fail("cannot create method call")

      val listAssigment = (psiFactory.createStatementFromText("java.util.List<java.lang.Integer> list = kek;", declaration)
        as PsiDeclarationStatement).declaredElements[0]
                            .toUElementOfType<ULocalVariable>() ?: fail("cannot create local variable")

      val methodCall = listAssigment.uastInitializer?.replace(callExpression) ?: fail("cannot replace!")
      assertEquals("a.method()", methodCall.sourcePsi?.text)
    }
  }

  @Test
  fun `test create if`() {
    ReadAction.runBlocking<RuntimeException> {
      val condition = psiFactory.createExpressionFromText("true", null).toUElementOfType<UExpression>()
                      ?: fail("cannot create condition")
      val thenBranch = psiFactory.createCodeBlockFromText("{a(b);}", null).toUElementOfType<UExpression>()
                       ?: fail("cannot create then branch")
      val elseBranch = psiFactory.createExpressionFromText("c++", null).toUElementOfType<UExpression>()
                       ?: fail("cannot create else branch")
      val ifExpression = uastElementFactory.createIfExpression(condition, thenBranch, elseBranch, null)
                         ?: fail("cannot create if expression")
      assertEquals("if (true) {a(b);} else c++;", ifExpression.sourcePsi?.text)
    }
  }

  @Test
  fun `test qualified reference`() {
    ReadAction.runBlocking<RuntimeException> {
      val reference = uastElementFactory.createQualifiedReference("java.util.List", myFixture.file)
      assertEquals("java.util.List", reference?.sourcePsi?.text)
    }
  }

  @Test
  fun `test create call expression with saved whitespace after`() {
    myFixture.configureByText("MyFile.java", """
      class A {
        void a() {
          a
            .b()
            .c<caret>()
            .d()
        }
      }
    """.trimIndent())

    ReadAction.runBlocking<RuntimeException> {
      val receiver = myFixture.file.findElementAt(myFixture.caretOffset)?.parentOfType<PsiCallExpression>().toUElementOfType<UCallExpression>()
                     ?: fail("Cannot find call expression")

      val callExpression = uastElementFactory.createCallExpression(
        receiver,
        "e",
        listOf(),
        null,
        UastCallKind.METHOD_CALL
      ) ?: fail("Cannot create call expression")

      assertEquals("""
      a
            .b()
            .c()
            .e()
    """.trimIndent(), callExpression.sourcePsi?.text)
    }
  }

  @Test
  fun `test create call expression with saved whitespace and dot after`() {
    myFixture.configureByText("MyFile.java", """
      class A {
        void a() {
          a.
            b().
            c<caret>().
            d()
        }
      }
    """.trimIndent())

    ReadAction.runBlocking<RuntimeException> {
      val receiver = myFixture.file.findElementAt(myFixture.caretOffset)?.parentOfType<PsiCallExpression>().toUElementOfType<UCallExpression>()
                     ?: fail("Cannot find call expression")

      val callExpression = uastElementFactory.createCallExpression(
        receiver,
        "e",
        listOf(),
        null,
        UastCallKind.METHOD_CALL
      ) ?: fail("Cannot create call expression")

      assertEquals("""
      a.
            b().
            c().
            e()
    """.trimIndent(), callExpression.sourcePsi?.text)
    }
  }

  @Test
  fun `test create call expression with saved whitespace and dot after with receiver from expression with field`() {
    myFixture.configureByText("MyFile.java", """
      class A {
        void a() {
          a.
            b().
            c<caret>().
            d
        }
      }
    """.trimIndent())

    ReadAction.runBlocking<RuntimeException> {
      val receiver = myFixture.file.findElementAt(myFixture.caretOffset)?.parentOfType<PsiCallExpression>().toUElementOfType<UCallExpression>()
                     ?: fail("Cannot find call expression")

      val callExpression = uastElementFactory.createCallExpression(
        receiver,
        "e",
        listOf(),
        null,
        UastCallKind.METHOD_CALL
      ) ?: fail("Cannot create call expression")

      assertEquals("""
      a.
            b().
            c().
            e()
    """.trimIndent(), callExpression.sourcePsi?.text)
    }
  }

  @Test
  fun `test initialize field`() {
    val psiFile = myFixture.configureByText("MyClass.java", """
      class My<caret>Class {
        String field;
        void method(String value) {
        }
      }
    """.trimIndent())
    initializeField()
    assertEquals("""
      class MyClass {
        String field;
        void method(String value) {
            field = value;
        }
      }
    """.trimIndent(), ReadAction.computeBlocking<String, RuntimeException> { psiFile.text })
  }

  @Test
  fun `test initialize field in method with whitespace`() {
    val psiFile = myFixture.configureByText("MyClass.java", """
      class My<caret>Class {
        String field;
        void method(String value) {
          
        }
      }
    """.trimIndent())
    initializeField()
    assertEquals("""
      class MyClass {
        String field;
        void method(String value) {
            field = value;
        }
      }
    """.trimIndent(), ReadAction.computeBlocking<String, RuntimeException> { psiFile.text })
  }

  @Test
  fun `test initialize field in method with statement`() {
    val psiFile = myFixture.configureByText("MyClass.java", """
      class My<caret>Class {
        String field;
        void method(String value) {
            int i = 0;
        }
      }
    """.trimIndent())
    initializeField()
    assertEquals("""
      class MyClass {
        String field;
        void method(String value) {
            int i = 0;
            field = value;
        }
      }
    """.trimIndent(), ReadAction.computeBlocking<String, RuntimeException> { psiFile.text })
  }

  @Test
  fun `test initialize field with same name`() {
    val psiFile = myFixture.configureByText("MyClass.java", """
      class My<caret>Class {
        String field;
        void method(String field) {
        }
      }
    """.trimIndent())
    initializeField()
    assertEquals("""
      class MyClass {
        String field;
        void method(String field) {
            this.field = field;
        }
      }
    """.trimIndent(), ReadAction.computeBlocking<String, RuntimeException> { psiFile.text })
  }

  private fun initializeField() {
    val (uField, uParameter) = ReadAction.computeBlocking<Pair<UField, UParameter>, RuntimeException> {
      val uClass =
        myFixture.file.findElementAt(myFixture.caretOffset)?.parentOfType<PsiClass>().toUElementOfType<UClass>()
        ?: fail("Cannot find UClass")
      val uField = uClass.fields.firstOrNull() ?: fail("Cannot find field")
      val uParameter = uClass.methods.find { it.name == "method"}?.uastParameters?.firstOrNull() ?: fail("Cannot find parameter")
      uField to uParameter
    }

    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      val expression = generatePlugin.initializeField(uField, uParameter)
      assertNotNull(expression)
    }
  }
}