// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java.generate

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.*
import junit.framework.TestCase
import org.jetbrains.uast.*
import org.jetbrains.uast.generate.UastCodeGenerationPlugin
import org.jetbrains.uast.generate.refreshed
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.test.java.AbstractJavaUastLightTest

class JavaUastGenerationTest : AbstractJavaUastLightTest() {

  private val psiFactory: PsiElementFactory
    get() = JavaPsiFacade.getElementFactory(myFixture.project)
  private val generatePlugin: UastCodeGenerationPlugin
    get() = UastCodeGenerationPlugin.byLanguage(JavaLanguage.INSTANCE)!!

  fun `test logical and operation with simple operands`() {
    val left = psiFactory.createExpressionFromText("true", null).toUElementOfType<UExpression>()
               ?: fail("Cannot create left UExpression")
    val right = psiFactory.createExpressionFromText("false", null).toUElementOfType<UExpression>()
                ?: fail("Cannot create right UExpression")

    val expression = generatePlugin.createBinaryExpression(left, right, UastBinaryOperator.LOGICAL_AND)
                     ?: fail("Cannot create expression")

    TestCase.assertEquals("true && false", expression.sourcePsi?.text)
  }

  fun `test logical and operation with simple operands with parenthesis`() {
    val left = psiFactory.createExpressionFromText("(true)", null).toUElementOfType<UExpression>()
               ?: fail("Cannot create left UExpression")
    val right = psiFactory.createExpressionFromText("(false)", null).toUElementOfType<UExpression>()
                ?: fail("Cannot create right UExpression")

    val expression = generatePlugin.createFlatBinaryExpression(left, right, UastBinaryOperator.LOGICAL_AND)
                     ?: fail("Cannot create expression")

    TestCase.assertTrue(expression.sourcePsi is PsiBinaryExpression)
    TestCase.assertEquals("true && false", expression.sourcePsi?.text)
  }

  fun `test logical and operation with simple operands with parenthesis polyadic`() {
    val left = psiFactory.createExpressionFromText("(true && false)", null).toUElementOfType<UExpression>()
               ?: fail("Cannot create left UExpression")
    val right = psiFactory.createExpressionFromText("(false)", null).toUElementOfType<UExpression>()
                ?: fail("Cannot create right UExpression")

    val expression = generatePlugin.createFlatBinaryExpression(left, right, UastBinaryOperator.LOGICAL_AND)
                     ?: fail("Cannot create expression")

    TestCase.assertTrue(expression.sourcePsi is PsiPolyadicExpression)
    TestCase.assertEquals("true && false && false", expression.sourcePsi?.text)
  }

  fun `test simple reference creating from variable`() {
    val variable = psiFactory.createVariableDeclarationStatement(
      "a",
      PsiType.INT
      , null
    ).declaredElements.getOrNull(0)?.toUElementOfType<UVariable>() ?: fail("cannot create variable")

    val reference = generatePlugin.createSimpleReference(variable) ?: fail("cannot create reference")
    TestCase.assertEquals("a", reference.identifier)
  }

  fun `test simple reference by name`() {
    val reference = generatePlugin.createSimpleReference("a", myFixture.project) ?: fail("cannot create reference")
    TestCase.assertEquals("a", reference.identifier)
  }

  fun `test parenthesised expression`() {
    val expression = psiFactory.createExpressionFromText("a + b", null).toUElementOfType<UExpression>()
                     ?: fail("cannot create expression")
    val parenthesizedExpression = generatePlugin.createParenthesizedExpression(expression)
                                  ?: fail("cannot create parenthesized expression")

    TestCase.assertEquals("(a + b)", parenthesizedExpression.sourcePsi?.text)
  }

  fun `test return expression`() {
    val expression = psiFactory.createExpressionFromText("a + b", null).toUElementOfType<UExpression>()
                     ?: fail("Cannot find plugin")

    val returnExpression = generatePlugin.createReturnExpresion(expression) ?: fail("cannot create return expression")

    TestCase.assertEquals("return a + b;", returnExpression.sourcePsi?.text)
  }

  fun `test variable declaration without type`() {
    val expression = psiFactory.createExpressionFromText("1 + 2", null).toUElementOfType<UExpression>()
                     ?: fail("cannot create variable declaration")

    val declaration = generatePlugin.createLocalVariable("a", null, expression) ?: fail("cannot create variable")

    TestCase.assertEquals("int a = 1 + 2;", declaration.sourcePsi?.text)
  }

  fun `test variable declaration with type`() {
    val expression = psiFactory.createExpressionFromText("b", null).toUElementOfType<UExpression>()
                     ?: fail("cannot create variable declaration")

    val declaration = generatePlugin.createLocalVariable("a", PsiType.DOUBLE, expression) ?: fail("cannot create variable")

    TestCase.assertEquals("double a = b;", declaration.sourcePsi?.text)
  }

  fun `test final variable declaration`() {
    val expression = psiFactory.createExpressionFromText("b", null).toUElementOfType<UExpression>()
                     ?: fail("cannot create variable declaration")

    val declaration = generatePlugin.createLocalVariable("a", PsiType.DOUBLE, expression, true) ?: fail("cannot create variable")

    TestCase.assertEquals("final double a = b;", declaration.sourcePsi?.text)
  }

  fun `test block expression`() {
    val statement1 = psiFactory.createStatementFromText("System.out.println();", null).toUElementOfType<UExpression>()
                     ?: fail("cannot create statement")
    val statement2 = psiFactory.createStatementFromText("System.out.println(2);", null).toUElementOfType<UExpression>()
                     ?: fail("cannot create statement")

    val block = generatePlugin.createBlockExpression(listOf(statement1, statement2), myFixture.project) ?: fail("cannot create block")

    TestCase.assertEquals("{" +
                          "System.out.println();" +
                          "System.out.println(2);" +
                          "}", block.sourcePsi?.text)
  }

  fun `test lambda expression`() {
    val statement = psiFactory.createStatementFromText("System.out.println();", null).toUElementOfType<UExpression>()
                    ?: fail("cannot create statement")

    val parameter1 = psiFactory.createParameter("a", PsiType.INT).toUElementOfType<UParameter>() ?: fail("cannot create parameter")

    val parameter2 = psiFactory.createParameter("b", PsiType.INT).toUElementOfType<UParameter>() ?: fail("cannot create parameter")
    (parameter2.sourcePsi as PsiParameter).typeElement?.delete()
    (parameter2.sourcePsi as PsiParameter).children[1].delete()

    val lambda = generatePlugin.createLambdaExpression(listOf(parameter1, parameter2), statement) ?: fail("cannot create lambda")

    TestCase.assertEquals("(a,b)->System.out.println()", lambda.sourcePsi?.text)
  }

  fun `test lambda expression with simplified block body`() {
    val block = psiFactory.createStatementFromText("{ return \"10\"; }", null).toUElementOfType<UBlockExpression>()
                ?: fail("cannot create block")

    val parameter1 = psiFactory.createParameter("a", PsiType.INT).toUElementOfType<UParameter>() ?: fail("cannot create parameter")
    (parameter1.sourcePsi as PsiParameter).typeElement?.delete()
    (parameter1.sourcePsi as PsiParameter).children[1].delete()
    val lambda = generatePlugin.createLambdaExpression(listOf(parameter1), block) ?: fail("cannot create lambda")
    TestCase.assertEquals("""a->"10"""", lambda.sourcePsi?.text)
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
    val variable = generatePlugin.createLocalVariable(null, PsiType.INT, expression, true)
                   ?: fail("cannot create variable")

    TestCase.assertEquals("final int i = f(a) + 1;", variable.sourcePsi?.text)
  }
}