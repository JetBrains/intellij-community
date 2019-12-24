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
import com.intellij.psi.impl.PsiDiamondTypeUtil
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.castSafelyTo
import com.siyeh.ig.psiutils.ParenthesesUtils
import org.jetbrains.uast.*
import org.jetbrains.uast.generate.UParameterInfo
import org.jetbrains.uast.generate.UastCodeGenerationPlugin
import org.jetbrains.uast.generate.UastElementFactory
import org.jetbrains.uast.java.*

internal class JavaUastCodeGenerationPlugin : UastCodeGenerationPlugin {
  override fun getElementFactory(project: Project): UastElementFactory = JavaUastElementFactory(project)

  override val language: Language
    get() = JavaLanguage.INSTANCE

  private fun cleanupMethodCall(methodCall: PsiMethodCallExpression): PsiMethodCallExpression {
    if (methodCall.typeArguments.isNotEmpty()) {
      val resolved = methodCall.resolveMethod() ?: return methodCall
      if (methodCall.typeArguments.size == resolved.typeParameters.size &&
          PsiDiamondTypeUtil.areTypeArgumentsRedundant(
            methodCall.typeArguments,
            methodCall,
            false,
            resolved,
            resolved.typeParameters
          )
      ) {
        val emptyTypeArgumentsMethodCall = JavaPsiFacade.getElementFactory(methodCall.project)
                                             .createExpressionFromText("foo()", null) as PsiMethodCallExpression

        methodCall.typeArgumentList.replace(emptyTypeArgumentsMethodCall.typeArgumentList)
      }
    }

    return methodCall
  }

  private fun adjustChainStyleToMethodCalls(oldPsi: PsiElement, newPsi: PsiElement) {
    if (oldPsi is PsiMethodCallExpression && newPsi is PsiMethodCallExpression &&
        oldPsi.methodExpression.qualifierExpression != null && newPsi.methodExpression.qualifier != null) {

      if (oldPsi.methodExpression.children.getOrNull(1) is PsiWhiteSpace &&
          newPsi.methodExpression.children.getOrNull(1) !is PsiWhiteSpace
      ) {
        newPsi.methodExpression.addAfter(oldPsi.methodExpression.children[1], newPsi.methodExpression.children[0])
      }
    }
  }

  override fun <T : UElement> replace(oldElement: UElement, newElement: T, elementType: Class<T>): T? {
    val oldPsi = oldElement.sourcePsi ?: return null
    val newPsi = newElement.sourcePsi ?: return null

    adjustChainStyleToMethodCalls(oldPsi, newPsi)

    val factory = JavaPsiFacade.getElementFactory(oldPsi.project)
    val updOldPsi = when {
      (newPsi is PsiBlockStatement || newPsi is PsiCodeBlock) && oldPsi.parent is PsiExpressionStatement -> oldPsi.parent
      else -> oldPsi
    }
    val updNewPsi = when {
      updOldPsi is PsiStatement && newPsi is PsiExpression -> factory.createExpresionStatement(newPsi) ?: return null
      updOldPsi is PsiCodeBlock && newPsi is PsiBlockStatement -> newPsi.codeBlock
      else -> newPsi
    }
    return when (val replaced = updOldPsi.replace(updNewPsi)) {
      is PsiExpressionStatement -> replaced.expression.toUElementOfExpectedTypes(elementType)
      is PsiMethodCallExpression -> cleanupMethodCall(replaced).toUElementOfExpectedTypes(elementType)
      else -> replaced.toUElementOfExpectedTypes(elementType)
    }
  }
}

private fun PsiElementFactory.createExpresionStatement(expression: PsiExpression): PsiStatement? {
  val statement = createStatementFromText("x;", null) as? PsiExpressionStatement ?: return null
  statement.expression.replace(expression)
  return statement
}

class JavaUastElementFactory(private val project: Project) : UastElementFactory {
  private val psiFactory: PsiElementFactory = JavaPsiFacade.getElementFactory(project)

  override fun createQualifiedReference(qualifiedName: String, context: UElement?): UQualifiedReferenceExpression? {
    return psiFactory.createExpressionFromText(qualifiedName, context?.sourcePsi)
      .castSafelyTo<PsiReferenceExpression>()
      ?.let { JavaUQualifiedReferenceExpression(it, null) }
  }

  override fun createIfExpression(condition: UExpression, thenBranch: UExpression, elseBranch: UExpression?): UIfExpression? {
    val conditionPsi = condition.sourcePsi ?: return null
    val thenBranchPsi = thenBranch.sourcePsi ?: return null

    val ifStatement = psiFactory.createStatementFromText("if (a) b else c", null) as? PsiIfStatement ?: return null
    ifStatement.condition?.replace(conditionPsi)

    ifStatement.thenBranch?.replace(thenBranchPsi.branchStatement ?: return null)
    elseBranch?.sourcePsi?.branchStatement?.let { ifStatement.elseBranch?.replace(it) } ?: ifStatement.elseBranch?.delete()

    return JavaUIfExpression(ifStatement, null)
  }

  override fun createCallExpression(receiver: UExpression?,
                                    methodName: String,
                                    parameters: List<UExpression>,
                                    expectedReturnType: PsiType?,
                                    kind: UastCallKind,
                                    context: PsiElement?): UCallExpression? {
    if (kind != UastCallKind.METHOD_CALL) return null

    val methodCall = psiFactory.createExpressionFromText(
      if (receiver != null) "a.b()" else "a()", context
    ) as? PsiMethodCallExpression ?: return null

    val methodIdentifier = psiFactory.createIdentifier(methodName)

    if (receiver != null) {
      methodCall.methodExpression.qualifierExpression?.replace(receiver.sourcePsi!!)
    }
    methodCall.methodExpression.referenceNameElement?.replace(methodIdentifier)

    for (parameter in parameters) {
      methodCall.argumentList.add(parameter.sourcePsi!!)
    }

    return if (expectedReturnType == null)
      JavaUCallExpression(methodCall, null)
    else
      MethodCallUpgradeHelper(project, methodCall, expectedReturnType).tryUpgradeToExpectedType()
        ?.let { JavaUCallExpression(it, null) }
  }

  private class MethodCallUpgradeHelper(val project: Project, val methodCall: PsiMethodCallExpression, val expectedReturnType: PsiType) {
    lateinit var resultType: PsiType

    fun tryUpgradeToExpectedType(): PsiMethodCallExpression? {
      resultType = methodCall.type ?: return null

      if (expectedReturnType.isAssignableFrom(resultType)) return methodCall

      if (!(resultType eqResolved expectedReturnType))
        return null

      return methodCall.methodExpression.qualifierExpression?.let { tryPickUpTypeParameters() }
    }

    private fun tryPickUpTypeParameters(): PsiMethodCallExpression? {
      val expectedTypeTypeParameters = expectedReturnType.castSafelyTo<PsiClassType>()?.parameters ?: return null
      val resultTypeTypeParameters = resultType.castSafelyTo<PsiClassType>()?.parameters ?: return null
      val notEqualTypeParametersIndices = expectedTypeTypeParameters
        .zip(resultTypeTypeParameters)
        .withIndex()
        .filterNot { (_, pair) -> PsiTypesUtil.compareTypes(pair.first, pair.second, false) }
        .map { (i, _) -> i }

      val resolvedMethod = methodCall.resolveMethod() ?: return null
      val methodReturnTypeTypeParameters = (resolvedMethod.returnType.castSafelyTo<PsiClassType>())?.parameters ?: return null
      val methodDefinitionTypeParameters = resolvedMethod.typeParameters
      val methodDefinitionToReturnTypeParametersMapping = methodDefinitionTypeParameters.map {
        it to methodReturnTypeTypeParameters.indexOfFirst { param -> it.name == param.canonicalText }
      }
        .filter { it.second != -1 }
        .toMap()

      val notMatchedTypesParametersNames = notEqualTypeParametersIndices
        .map { methodReturnTypeTypeParameters[it].canonicalText }
        .toSet()

      val callTypeParametersSubstitutor = methodCall.resolveMethodGenerics().substitutor
      val newParametersList = mutableListOf<PsiType>()
      for (parameter in methodDefinitionTypeParameters) {
        if (parameter.name in notMatchedTypesParametersNames) {
          newParametersList += expectedTypeTypeParameters[methodDefinitionToReturnTypeParametersMapping[parameter] ?: return null]
        }
        else {
          newParametersList += callTypeParametersSubstitutor.substitute(parameter) ?: return null
        }
      }

      return buildMethodCallFromNewTypeParameters(newParametersList)
    }

    private fun buildMethodCallFromNewTypeParameters(newParametersList: List<PsiType>): PsiMethodCallExpression? {
      val factory = JavaPsiFacade.getElementFactory(project)
      val expr = factory.createExpressionFromText(buildString {
        append("a.<")
        newParametersList.joinTo(this) { "T" }
        append(">b()")
      }, null) as? PsiMethodCallExpression ?: return null

      for ((i, param) in newParametersList.withIndex()) {
        val typeElement = factory.createTypeElement(param)
        expr.typeArgumentList.typeParameterElements[i].replace(typeElement)
      }

      methodCall.typeArgumentList.replace(expr.typeArgumentList)

      if (methodCall.type?.let { expectedReturnType.isAssignableFrom(it) } != true)
        return null

      return methodCall
    }

    infix fun PsiType.eqResolved(other: PsiType): Boolean {
      val resolvedThis = this.castSafelyTo<PsiClassType>()?.resolve() ?: return false
      val resolvedOther = other.castSafelyTo<PsiClassType>()?.resolve() ?: return false

      return PsiManager.getInstance(project).areElementsEquivalent(resolvedThis, resolvedOther)
    }
  }

  override fun createDeclarationExpression(declarations: List<UDeclaration>): UDeclarationsExpression? {
    return JavaUDeclarationsExpression(null, declarations)
  }

  override fun createReturnExpresion(expression: UExpression?,
                                     inLambda: Boolean): UReturnExpression? {
    val returnStatement = psiFactory.createStatementFromText("return ;", null) as? PsiReturnStatement ?: return null

    expression?.sourcePsi?.node?.let { (returnStatement as CompositeElement).addChild(it, returnStatement.lastChild.node) }

    return JavaUReturnExpression(returnStatement, null)
  }

  private fun PsiVariable.setMutability(immutable: Boolean) {
    if (immutable && !this.hasModifier(JvmModifier.FINAL)) {
      PsiUtil.setModifierProperty(this, PsiModifier.FINAL, true)
    }

    if (!immutable && this.hasModifier(JvmModifier.FINAL)) {
      PsiUtil.setModifierProperty(this, PsiModifier.FINAL, false)
    }
  }

  override fun createLocalVariable(suggestedName: String?, type: PsiType?, initializer: UExpression, immutable: Boolean): ULocalVariable? {
    val initializerPsi = initializer.sourcePsi as? PsiExpression ?: return null

    val name = createNameFromSuggested(
      suggestedName,
      VariableKind.LOCAL_VARIABLE,
      type,
      initializer = initializerPsi,
      context = initializerPsi
    ) ?: return null
    val variable = (type ?: initializer.getExpressionType())?.let { variableType ->
      psiFactory.createVariableDeclarationStatement(name, variableType, initializerPsi,
                                                    initializerPsi.context).declaredElements.firstOrNull() as? PsiLocalVariable
    } ?: return null

    variable.setMutability(immutable)

    return JavaULocalVariable(variable, null)
  }

  override fun createBlockExpression(expressions: List<UExpression>): UBlockExpression? {
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
        psi as? PsiStatement ?: (psi as? PsiExpression)?.let { psiFactory.createExpresionStatement(it) }
      }?.let { blockStatement.codeBlock.add(it) } ?: return null
    }

    return JavaUBlockExpression(blockStatement, null)
  }

  override fun createLambdaExpression(parameters: List<UParameterInfo>, body: UExpression): ULambdaExpression? {
    //TODO: smart handling cases, when parameters types should exist in code
    val lambda = psiFactory.createExpressionFromText(
      buildString {
        parameters.joinTo(this, separator = ",", prefix = "(", postfix = ")") { "x x" }
        append("->{}")
      },
      null
    ) as? PsiLambdaExpression ?: return null

    val needsType = parameters.all { it.type != null }
    lambda.parameterList.parameters.forEachIndexed { i, parameter ->
      parameters[i].also { parameterInfo ->
        val name = createNameFromSuggested(
          parameterInfo.suggestedName,
          VariableKind.PARAMETER,
          parameterInfo.type,
          context = body.sourcePsi
        ) ?: return null
        parameter.nameIdentifier?.replace(psiFactory.createIdentifier(name))

        if (needsType) {
          parameter.typeElement?.replace(psiFactory.createTypeElement(parameterInfo.type!!))
        }
        else {
          parameter.removeTypeElement()
        }
      }
    }

    if (lambda.parameterList.parametersCount == 1) {
      lambda.parameterList.parameters[0].removeTypeElement()
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

  private fun normalizeBlockForLambda(block: PsiCodeBlock): PsiElement =
    block
      .takeIf { block.statementCount == 1 }
      ?.let { block.statements[0] as? PsiReturnStatement }
      ?.returnValue
    ?: block

  override fun createParenthesizedExpression(expression: UExpression): UParenthesizedExpression? {
    val parenthesizedExpression = psiFactory.createExpressionFromText("()", null) as? PsiParenthesizedExpression ?: return null
    parenthesizedExpression.children.getOrNull(1)?.replace(expression.sourcePsi ?: return null) ?: return null
    return JavaUParenthesizedExpression(parenthesizedExpression, null)
  }

  override fun createSimpleReference(name: String): USimpleNameReferenceExpression? {
    val reference = psiFactory.createExpressionFromText(name, null)
    return JavaUSimpleNameReferenceExpression(reference, name, null)
  }

  override fun createSimpleReference(variable: UVariable): USimpleNameReferenceExpression? {
    return createSimpleReference(variable.name ?: return null)
  }

  override fun createBinaryExpression(leftOperand: UExpression,
                                      rightOperand: UExpression,
                                      operator: UastBinaryOperator): UBinaryExpression? {
    val leftPsi = leftOperand.sourcePsi ?: return null
    val rightPsi = rightOperand.sourcePsi ?: return null

    val operatorSymbol = when (operator) {
      UastBinaryOperator.LOGICAL_AND -> "&&"
      else -> return null
    }
    val psiBinaryExpression = psiFactory.createExpressionFromText("a $operatorSymbol b", null) as? PsiBinaryExpression
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
    val dummyParent = psiFactory.createStatementFromText("a;", null)
    dummyParent.firstChild.replace(binarySourcePsi)
    ParenthesesUtils.removeParentheses(dummyParent.firstChild as PsiExpression, false)
    return when (val result = dummyParent.firstChild) {
      is PsiBinaryExpression -> JavaUBinaryExpression(result, null)
      is PsiPolyadicExpression -> JavaUPolyadicExpression(result, null)
      else -> error("Unexpected type " + result.javaClass)
    }
  }

  private val PsiElement.branchStatement: PsiStatement?
    get() = when (this) {
      is PsiExpression -> JavaPsiFacade.getElementFactory(project).createExpresionStatement(this)
      is PsiCodeBlock -> BlockUtils.createBlockStatement(project).also { it.codeBlock.replace(this) }
      is PsiStatement -> this
      else -> null
    }

  private fun PsiVariable.removeTypeElement() {
    this.typeElement?.delete()
    if (this.children.getOrNull(1) !is PsiIdentifier) {
      this.children.getOrNull(1)?.delete()
    }
  }

  private fun createNameFromSuggested(suggestedName: String?,
                                      variableKind: VariableKind,
                                      type: PsiType? = null,
                                      initializer: PsiExpression? = null,
                                      context: PsiElement? = null): String? {
    val codeStyleManager = JavaCodeStyleManager.getInstance(project)
    val name = suggestedName ?: codeStyleManager.generateVariableName(type, initializer, variableKind) ?: return null
    return codeStyleManager.suggestUniqueVariableName(name, context, false)
  }

  private fun JavaCodeStyleManager.generateVariableName(type: PsiType?, initializer: PsiExpression?, kind: VariableKind) =
    suggestVariableName(kind, null, initializer, type).names.firstOrNull()
}