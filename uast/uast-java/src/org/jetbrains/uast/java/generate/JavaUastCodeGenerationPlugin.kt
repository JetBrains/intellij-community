// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.java.generate

import com.intellij.codeInsight.BlockUtils
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.VariableKind
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.util.PsiUtil
import com.siyeh.ig.psiutils.ParenthesesUtils
import org.jetbrains.uast.*
import org.jetbrains.uast.generate.UastCodeGenerationPlugin
import org.jetbrains.uast.java.*
import java.lang.AssertionError

class JavaUastCodeGenerationPlugin : UastCodeGenerationPlugin {
  override fun <T: UElement> replace(oldElement: UElement, newElement: T, elementType: Class<T>): T? =
    newElement.sourcePsi?.let { oldElement.sourcePsi?.replace(it) }?.toUElementOfExpectedTypes(elementType)

  override fun createDeclarationExpression(declarations: List<UDeclaration>, project: Project): UDeclarationsExpression? {
    return JavaUDeclarationsExpression(null, declarations)
  }

  private fun PsiElementFactory.createExpresionStatement(expression: PsiExpression): PsiStatement? {
    val statement = createStatementFromText("x;", null) as? PsiExpressionStatement ?: return null
    statement.expression.replace(expression)
    return statement
  }

  override fun createReturnExpresion(expression: UExpression, inLambda: Boolean): UReturnExpression? {
    val factory = JavaPsiFacade.getElementFactory(expression.sourcePsi?.project ?: return null)
    val returnStatement = factory.createStatementFromText("return ;", null) as? PsiReturnStatement ?: return null

    (returnStatement as CompositeElement).addChild(expression.sourcePsi!!.node, returnStatement.lastChild.node)

    return JavaUReturnExpression(returnStatement, null)
  }

  override fun createLocalVariable(name: String?, type: PsiType?, initializer: UExpression, immutable: Boolean): ULocalVariable? {
    val initializerPsi = initializer.sourcePsi as? PsiExpression ?: return null

    if (name == null) {
      return createLocalVariableWithSuggestedName(type, initializer, immutable)
    }

    val factory = JavaPsiFacade.getElementFactory(initializerPsi.project)
    val variable = (type ?: initializer.getExpressionType())?.let { variableType ->
      factory.createVariableDeclarationStatement(name, variableType, initializerPsi,
                                                 initializerPsi.context).declaredElements.firstOrNull() as? PsiLocalVariable
    } ?: return null

    if (immutable && !variable.hasModifier(JvmModifier.FINAL)) {
      PsiUtil.setModifierProperty(variable, PsiModifier.FINAL, true)
    }

    if (!immutable && variable.hasModifier(JvmModifier.FINAL)) {
      PsiUtil.setModifierProperty(variable, PsiModifier.FINAL, false)
    }

    return JavaULocalVariable(variable, null)
  }

  private fun createLocalVariableWithSuggestedName(type: PsiType?, initializer: UExpression, immutable: Boolean): ULocalVariable? {
    val initializerPsi = initializer.sourcePsi as? PsiExpression ?: return null
    val codeStyleManager = JavaCodeStyleManager.getInstance(initializerPsi.project)
    val resultType = type ?: initializerPsi.type ?: return null
    var suggestedNameInfo = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, initializerPsi, resultType)
    suggestedNameInfo = codeStyleManager.suggestUniqueVariableName(suggestedNameInfo, initializerPsi, true)
    val name = suggestedNameInfo.names.firstOrNull() ?: return null
    return createLocalVariable(name, type, initializer, immutable)
  }

  override fun createBlockExpression(expressions: List<UExpression>, project: Project): UBlockExpression? {
    val factory = JavaPsiFacade.getElementFactory(project)
    val blockStatement = BlockUtils.createBlockStatement(project)

    for (expression in expressions) {
      if (expression is JavaUDeclarationsExpression) {
        for (declaration in expression.declarations) {
          if (declaration.sourcePsi is PsiLocalVariable) {
            blockStatement.codeBlock.add(declaration.sourcePsi?.parent as? PsiDeclarationStatement ?: return null)
          }
        }
        continue
      }

      expression.sourcePsi?.let { psi ->
        psi as? PsiStatement ?: (psi as? PsiExpression)?.let { factory.createExpresionStatement(it) }
      }?.let { blockStatement.codeBlock.add(it) } ?: return null
    }

    return JavaUBlockExpression(blockStatement, null)
  }

  override fun createLambdaExpression(parameters: List<UParameter>, body: UExpression): ULambdaExpression? {
    val factory = JavaPsiFacade.getElementFactory(body.sourcePsi?.project ?: return null)
    val lambda = factory.createExpressionFromText(
      buildString {
        parameters.joinTo(this, separator = ",", prefix = "(", postfix = ")") { "x" }

        append("->")
        append("{}")
      },
      null
    ) as? PsiLambdaExpression ?: return null

    lambda.parameterList.parameters.forEachIndexed { i, p ->
      p.replace(parameters[i].sourcePsi ?: return null)
    }

    if (lambda.parameterList.parameters.any { it.typeElement == null })
      lambda.parameterList.parameters.forEach {
        it.typeElement?.delete()
        if (it.children.getOrNull(1) !is PsiIdentifier)
          it.children.getOrNull(1)?.delete()
      }

    if (lambda.parameterList.parametersCount == 1 && lambda.parameterList.parameters[0].typeElement == null) {
      lambda.parameterList.firstChild.delete()
      lambda.parameterList.lastChild.delete()
    }

    val normalizedBody = when (val bodyPsi = body.sourcePsi) {
      is PsiExpression -> bodyPsi
      is PsiCodeBlock -> normalizeBlockForLambda(bodyPsi)
      is PsiBlockStatement -> normalizeBlockForLambda(bodyPsi.codeBlock)
      else -> return null
    }

    lambda.body?.replace(normalizedBody) ?: return null

    return JavaULambdaExpression(lambda, null)
  }

  private fun normalizeBlockForLambda(block: PsiCodeBlock): PsiElement = block
                                                                           .takeIf { block.statementCount == 1 }
                                                                           ?.let { block.statements[0] as? PsiReturnStatement }
                                                                           ?.let { it.returnValue } ?: block

  override fun createParenthesizedExpression(expression: UExpression): UParenthesizedExpression? {
    val factory = JavaPsiFacade.getElementFactory(expression.sourcePsi?.project ?: return null)
    val parenthesizedExpression = factory.createExpressionFromText("()", null) as? PsiParenthesizedExpression ?: return null
    parenthesizedExpression.children.getOrNull(1)?.replace(expression.sourcePsi ?: return null) ?: return null
    return JavaUParenthesizedExpression(parenthesizedExpression, null)
  }

  override fun createSimpleReference(name: String, project: Project): USimpleNameReferenceExpression? {
    val factory = JavaPsiFacade.getElementFactory(project)
    val reference = factory.createExpressionFromText(name, null)
    return JavaUSimpleNameReferenceExpression(reference, name, null)
  }

  override fun createSimpleReference(variable: UVariable): USimpleNameReferenceExpression? {
    return createSimpleReference(variable.name ?: return null, variable.sourcePsi?.project ?: return null)
  }

  override val language: Language
    get() = JavaLanguage.INSTANCE


  override fun createBinaryExpression(leftOperand: UExpression,
                                      rightOperand: UExpression,
                                      operator: UastBinaryOperator): UBinaryExpression? {
    val project = leftOperand.sourcePsi?.project ?: return null
    val leftPsi = leftOperand.sourcePsi ?: return null
    val rightPsi = rightOperand.sourcePsi ?: return null
    val factory = JavaPsiFacade.getElementFactory(project)

    val operatorSymbol = when (operator) {
      UastBinaryOperator.LOGICAL_AND -> "&&"
      else -> return null
    }
    val psiBinaryExpression = factory.createExpressionFromText("a $operatorSymbol b", null) as? PsiBinaryExpression
      ?: return null
    psiBinaryExpression.lOperand.replace(leftPsi)
    psiBinaryExpression.rOperand?.replace(rightPsi)

    return JavaUBinaryExpression(psiBinaryExpression, null)
  }

  override fun createFlatBinaryExpression(leftOperand: UExpression,
                                          rightOperand: UExpression,
                                          operator: UastBinaryOperator): UPolyadicExpression? {
    val binaryExpression = createBinaryExpression(leftOperand, rightOperand, operator) ?: return null
    val binarySourcePsi = binaryExpression.sourcePsi as? PsiBinaryExpression ?: return null
    val factory = JavaPsiFacade.getElementFactory(binarySourcePsi.project)
    val dummyParent = factory.createStatementFromText("a;", null)
    dummyParent.firstChild.replace(binarySourcePsi)
    ParenthesesUtils.removeParentheses(dummyParent.firstChild as PsiExpression, false)
    return when(val result = dummyParent.firstChild) {
      is PsiBinaryExpression -> JavaUBinaryExpression(result, null)
      is PsiPolyadicExpression -> JavaUPolyadicExpression(result, null)
      else -> throw AssertionError("Unexpected type " + result.javaClass)
    }
  }
}