// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java.generate

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import com.intellij.psi.util.parentOfType
import junit.framework.TestCase
import org.jetbrains.uast.*
import org.jetbrains.uast.generate.UParameterInfo
import org.jetbrains.uast.generate.UastCodeGenerationPlugin
import org.jetbrains.uast.generate.refreshed
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.test.java.AbstractJavaUastLightTest

class JavaUastGenerationTest : AbstractJavaUastLightTest() {

  private val psiFactory: PsiElementFactory
    get() = JavaPsiFacade.getElementFactory(myFixture.project)
  private val generatePlugin: UastCodeGenerationPlugin
    get() = UastCodeGenerationPlugin.byLanguage(JavaLanguage.INSTANCE)!!
  private val uastElementFactory
    get() = generatePlugin.getElementFactory(myFixture.project)

  fun `test logical and operation with simple operands`() {
    val left = psiFactory.createExpressionFromText("true", null).toUElementOfType<UExpression>()
               ?: fail("Cannot create left UExpression")
    val right = psiFactory.createExpressionFromText("false", null).toUElementOfType<UExpression>()
                ?: fail("Cannot create right UExpression")

    val expression = uastElementFactory.createBinaryExpression(left, right, UastBinaryOperator.LOGICAL_AND, null)
                     ?: fail("Cannot create expression")

    TestCase.assertEquals("true && false", expression.sourcePsi?.text)
  }

  fun `test logical and operation with simple operands with parenthesis`() {
    val left = psiFactory.createExpressionFromText("(true)", null).toUElementOfType<UExpression>()
               ?: fail("Cannot create left UExpression")
    val right = psiFactory.createExpressionFromText("(false)", null).toUElementOfType<UExpression>()
                ?: fail("Cannot create right UExpression")

    val expression = uastElementFactory.createFlatBinaryExpression(left, right, UastBinaryOperator.LOGICAL_AND, null)
                     ?: fail("Cannot create expression")

    TestCase.assertTrue(expression.sourcePsi is PsiBinaryExpression)
    TestCase.assertEquals("true && false", expression.sourcePsi?.text)
  }

  fun `test logical and operation with simple operands with parenthesis polyadic`() {
    val left = psiFactory.createExpressionFromText("(true && false)", null).toUElementOfType<UExpression>()
               ?: fail("Cannot create left UExpression")
    val right = psiFactory.createExpressionFromText("(false)", null).toUElementOfType<UExpression>()
                ?: fail("Cannot create right UExpression")

    val expression = uastElementFactory.createFlatBinaryExpression(left, right, UastBinaryOperator.LOGICAL_AND, null)
                     ?: fail("Cannot create expression")

    TestCase.assertTrue(expression.sourcePsi is PsiPolyadicExpression)
    TestCase.assertEquals("true && false && false", expression.sourcePsi?.text)
  }

  fun `test simple reference creating from variable`() {
    val variable = psiFactory.createVariableDeclarationStatement(
      "a",
      PsiType.INT, null
    ).declaredElements.getOrNull(0)?.toUElementOfType<UVariable>() ?: fail("cannot create variable")

    val reference = uastElementFactory.createSimpleReference(variable, null) ?: fail("cannot create reference")
    TestCase.assertEquals("a", reference.identifier)
  }

  fun `test simple reference by name`() {
    val reference = uastElementFactory.createSimpleReference("a", null) ?: fail("cannot create reference")
    TestCase.assertEquals("a", reference.identifier)
  }

  fun `test parenthesised expression`() {
    val expression = psiFactory.createExpressionFromText("a + b", null).toUElementOfType<UExpression>()
                     ?: fail("cannot create expression")
    val parenthesizedExpression = uastElementFactory.createParenthesizedExpression(expression, null)
                                  ?: fail("cannot create parenthesized expression")

    TestCase.assertEquals("(a + b)", parenthesizedExpression.sourcePsi?.text)
  }

  fun `test return expression`() {
    val expression = psiFactory.createExpressionFromText("a + b", null).toUElementOfType<UExpression>()
                     ?: fail("Cannot find plugin")

    val returnExpression = uastElementFactory.createReturnExpression(expression, false, null) ?: fail("cannot create return expression")

    TestCase.assertEquals("return a + b;", returnExpression.sourcePsi?.text)
  }

  fun `test variable declaration without type`() {
    val expression = psiFactory.createExpressionFromText("1 + 2", null).toUElementOfType<UExpression>()
                     ?: fail("cannot create variable declaration")

    val declaration = uastElementFactory.createLocalVariable("a", null, expression, false, null) ?: fail("cannot create variable")

    TestCase.assertEquals("int a = 1 + 2;", declaration.sourcePsi?.text)
  }

  fun `test variable declaration with type`() {
    val expression = psiFactory.createExpressionFromText("b", null).toUElementOfType<UExpression>()
                     ?: fail("cannot create variable declaration")

    val declaration = uastElementFactory.createLocalVariable("a", PsiType.DOUBLE, expression, false, null) ?: fail("cannot create variable")

    TestCase.assertEquals("double a = b;", declaration.sourcePsi?.text)
  }

  fun `test final variable declaration`() {
    val expression = psiFactory.createExpressionFromText("b", null).toUElementOfType<UExpression>()
                     ?: fail("cannot create variable declaration")

    val declaration = uastElementFactory.createLocalVariable("a", PsiType.DOUBLE, expression, true, null)
                      ?: fail("cannot create variable")

    TestCase.assertEquals("final double a = b;", declaration.sourcePsi?.text)
  }

  fun `test final variable declaration with unique name`() {
    val context = psiFactory.createVariableDeclarationStatement("a", PsiType.INT, null, null)
    val expression = psiFactory.createExpressionFromText("b", context).toUElementOfType<UExpression>()
                     ?: fail("cannot create variable declaration")

    val declaration = uastElementFactory.createLocalVariable("a", PsiType.DOUBLE, expression, true, null)
                      ?: fail("cannot create variable")

    TestCase.assertEquals("final double a1 = b;", declaration.sourcePsi?.text)
  }

  fun `test block expression`() {
    val statement1 = psiFactory.createStatementFromText("System.out.println();", null).toUElementOfType<UExpression>()
                     ?: fail("cannot create statement")
    val statement2 = psiFactory.createStatementFromText("System.out.println(2);", null).toUElementOfType<UExpression>()
                     ?: fail("cannot create statement")

    val block = uastElementFactory.createBlockExpression(listOf(statement1, statement2), null) ?: fail("cannot create block")

    TestCase.assertEquals("{" +
                          "System.out.println();" +
                          "System.out.println(2);" +
                          "}", block.sourcePsi?.text)
  }

  fun `test lambda expression`() {
    val statement = psiFactory.createStatementFromText("System.out.println();", null).toUElementOfType<UExpression>()
                    ?: fail("cannot create statement")

    val lambda = uastElementFactory.createLambdaExpression(
      listOf(
        UParameterInfo(PsiType.INT, "a"),
        UParameterInfo(null, "b")
      ),
      statement,
      null) ?: fail("cannot create lambda")

    TestCase.assertEquals("(a,b)->System.out.println()", lambda.sourcePsi?.text)
  }

  fun `test lambda expression with explicit types`() {
    val statement = psiFactory.createStatementFromText("System.out.println();", null).toUElementOfType<UExpression>()
                    ?: fail("cannot create statement")

    val lambda = uastElementFactory.createLambdaExpression(
      listOf(
        UParameterInfo(PsiType.INT, "a"),
        UParameterInfo(PsiType.DOUBLE, "b")
      ),
      statement,
      null) ?: fail("cannot create lambda")

    TestCase.assertEquals("(int a,double b)->System.out.println()", lambda.sourcePsi?.text)
  }

  fun `test lambda expression with simplified block body`() {
    val block = psiFactory.createStatementFromText("{ return \"10\"; }", null).toUElementOfType<UBlockExpression>()
                ?: fail("cannot create block")

    val lambda = uastElementFactory.createLambdaExpression(listOf(UParameterInfo(null, "a")), block, null)
                 ?: fail("cannot create lambda")
    TestCase.assertEquals("""a->"10"""", lambda.sourcePsi?.text)
  }

  fun `test lambda expression with simplified block body with context`() {
    val context = psiFactory.createVariableDeclarationStatement("a", PsiType.INT, null)
    val block = psiFactory.createStatementFromText("{ return \"10\"; }", context).toUElementOfType<UBlockExpression>()
                ?: fail("cannot create block")

    val lambda = uastElementFactory.createLambdaExpression(listOf(UParameterInfo(null, "a")), block, null)
                 ?: fail("cannot create lambda")
    TestCase.assertEquals("""a1->"10"""", lambda.sourcePsi?.text)
  }

  fun `test function argument replacement`() {
    val expression = psiFactory.createExpressionFromText("f(a)", null).toUElementOfType<UCallExpression>()
                     ?: fail("cannot create expression")
    val newArgument = psiFactory.createExpressionFromText("b", null).toUElementOfType<USimpleNameReferenceExpression>()
                      ?: fail("cannot create reference")

    TestCase.assertNotNull(expression.valueArguments[0].replace(newArgument))
    val updated = expression.refreshed() ?: fail("cannot update expression")
    TestCase.assertEquals("f", updated.methodName)
    TestCase.assertTrue(updated.valueArguments[0] is USimpleNameReferenceExpression)
    TestCase.assertEquals("b", (updated.valueArguments[0] as USimpleNameReferenceExpression).identifier)
  }

  fun `test suggested name`() {
    val expression = psiFactory.createExpressionFromText("f(a) + 1", null).toUElementOfType<UExpression>()
                     ?: fail("cannot create expression")
    val variable = uastElementFactory.createLocalVariable(null, PsiType.INT, expression, true, null)
                   ?: fail("cannot create variable")

    TestCase.assertEquals("final int i = f(a) + 1;", variable.sourcePsi?.text)
  }

  fun `test method call generation with receiver`() {
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

    TestCase.assertEquals(""""10".substring(1, 2)""", methodCall.sourcePsi?.text)
  }

  fun `test method call generation without receiver`() {
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

    TestCase.assertEquals("""substring(1, 2)""", methodCall.sourcePsi?.text)
  }

  fun `test method call generation with generics restoring`() {
    val arrays = psiFactory.createExpressionFromText("java.util.Arrays", null).toUElementOfType<UExpression>()
                 ?: fail("cannot create receiver")
    val methodCall = uastElementFactory.createCallExpression(
      arrays,
      "asList",
      listOf(),
      psiFactory.createTypeFromText("java.util.List<java.lang.String>", null),
      UastCallKind.METHOD_CALL
    ) ?: fail("cannot create call")
    TestCase.assertEquals("java.util.Arrays.<String>asList()", methodCall.sourcePsi?.text)
  }

  fun `test method call generation with generics restoring 2 parameters`() {
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
    TestCase.assertEquals("java.util.Collections.<String, Integer>emptyMap()", methodCall.sourcePsi?.text)
  }

  fun `test method call generation with generics restoring 1 parameter with 1 existing`() {
    val newClass = myFixture.addClass("""
      class A {
        public static <T1, T2> java.util.Map<T1, T2> kek(T2 a) {
          return null;
        }
      }
    """.trimIndent())
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

    TestCase.assertEquals("A.<String, Integer>kek(\"a\")", methodCall.sourcePsi?.text)
  }

  fun `test method call generation with generics restoring 1 parameter with 1 unused `() {
    val newClass = myFixture.addClass("""
      class A {
        public static <T1, T2, T3> java.util.Map<T1, T3> kek(T1 a) {
          return null;
        }
      }
    """.trimIndent())
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

    TestCase.assertEquals("A.<String, Object, Integer>kek(\"a\")", methodCall.sourcePsi?.text)
  }

  fun `test method call generation with generics with context`() {
    val newClass = myFixture.addClass("""
      class A {
        public <T> java.util.List<T> method();
      }
    """.trimIndent())

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

    TestCase.assertEquals("a.<Integer>method()", callExpression.sourcePsi?.text)
  }

  fun `test callable reference generation with receiver`() {
    val receiver = uastElementFactory.createQualifiedReference("java.util.Arrays", myFixture.file)
                   ?: fail("failed to create receiver")
    val methodReference = uastElementFactory.createCallableReferenceExpression(receiver, "asList", myFixture.file)
                          ?: fail("failed to create method reference")
    TestCase.assertEquals(methodReference.sourcePsi?.text, "java.util.Arrays::asList")
  }

  fun `test removing unnecessary type parameters while replace`() {
    val newClass = myFixture.addClass("""
      class A {
        public <T> java.util.List<T> method();
      }
    """.trimIndent())

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
    TestCase.assertEquals("a.method()", methodCall.sourcePsi?.text)
  }

  fun `test create if`() {
    val condition = psiFactory.createExpressionFromText("true", null).toUElementOfType<UExpression>()
                    ?: fail("cannot create condition")
    val thenBranch = psiFactory.createCodeBlockFromText("{a(b);}", null).toUElementOfType<UExpression>()
                     ?: fail("cannot create then branch")
    val elseBranch = psiFactory.createExpressionFromText("c++", null).toUElementOfType<UExpression>()
                     ?: fail("cannot create else branch")
    val ifExpression = uastElementFactory.createIfExpression(condition, thenBranch, elseBranch, null)
                       ?: fail("cannot create if expression")
    TestCase.assertEquals("if (true) {a(b);} else c++;", ifExpression.sourcePsi?.text)
  }

  fun `test qualified reference`() {
    val reference = uastElementFactory.createQualifiedReference("java.util.List", myFixture.file)
    TestCase.assertEquals("java.util.List", reference?.sourcePsi?.text)
  }

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

    val receiver = myFixture.file.findElementAt(myFixture.caretOffset)?.parentOfType<PsiCallExpression>().toUElementOfType<UCallExpression>()
                   ?: fail("Cannot find call expression")

    val callExpression = uastElementFactory.createCallExpression(
      receiver,
      "e",
      listOf(),
      null,
      UastCallKind.METHOD_CALL
    ) ?: fail("Cannot create call expression")

    TestCase.assertEquals("""
      a
            .b()
            .c()
            .e()
    """.trimIndent(), callExpression.sourcePsi?.text)
  }

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

    val receiver = myFixture.file.findElementAt(myFixture.caretOffset)?.parentOfType<PsiCallExpression>().toUElementOfType<UCallExpression>()
                   ?: fail("Cannot find call expression")

    val callExpression = uastElementFactory.createCallExpression(
      receiver,
      "e",
      listOf(),
      null,
      UastCallKind.METHOD_CALL
    ) ?: fail("Cannot create call expression")

    TestCase.assertEquals("""
      a.
            b().
            c().
            e()
    """.trimIndent(), callExpression.sourcePsi?.text)
  }

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

    val receiver = myFixture.file.findElementAt(myFixture.caretOffset)?.parentOfType<PsiCallExpression>().toUElementOfType<UCallExpression>()
                   ?: fail("Cannot find call expression")

    val callExpression = uastElementFactory.createCallExpression(
      receiver,
      "e",
      listOf(),
      null,
      UastCallKind.METHOD_CALL
    ) ?: fail("Cannot create call expression")

    TestCase.assertEquals("""
      a.
            b().
            c().
            e()
    """.trimIndent(), callExpression.sourcePsi?.text)
  }

  fun `test initialize field`() {
    val psiFile = myFixture.configureByText("MyClass.java", """
      class My<caret>Class {
        String field;
        void method(String value) {
        }
      }
    """.trimIndent())
    initializeField()
    TestCase.assertEquals("""
      class MyClass {
        String field;
        void method(String value) {
            field = value;
        }
      }
    """.trimIndent(), psiFile.text)
  }

  fun `test initialize field in method with whitespace`() {
    val psiFile = myFixture.configureByText("MyClass.java", """
      class My<caret>Class {
        String field;
        void method(String value) {
          
        }
      }
    """.trimIndent())
    initializeField()
    TestCase.assertEquals("""
      class MyClass {
        String field;
        void method(String value) {
            field = value;
        }
      }
    """.trimIndent(), psiFile.text)
  }

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
    TestCase.assertEquals("""
      class MyClass {
        String field;
        void method(String value) {
            int i = 0;
            field = value;
        }
      }
    """.trimIndent(), psiFile.text)
  }

  fun `test initialize field with same name`() {
    val psiFile = myFixture.configureByText("MyClass.java", """
      class My<caret>Class {
        String field;
        void method(String field) {
        }
      }
    """.trimIndent())
    initializeField()
    TestCase.assertEquals("""
      class MyClass {
        String field;
        void method(String field) {
            this.field = field;
        }
      }
    """.trimIndent(), psiFile.text)
  }

  private fun initializeField() {
    val uClass =
      myFixture.file.findElementAt(myFixture.caretOffset)?.parentOfType<PsiClass>().toUElementOfType<UClass>()
      ?: fail("Cannot find UClass")
    val uField = uClass.fields.firstOrNull() ?: fail("Cannot find field")
    val uParameter = uClass.methods.find { it.name == "method"}?.uastParameters?.firstOrNull() ?: fail("Cannot find parameter")

    WriteCommandAction.runWriteCommandAction(project) {
      val expression = generatePlugin.initializeField(uField, uParameter)
      assertNotNull(expression)
    }
  }
}