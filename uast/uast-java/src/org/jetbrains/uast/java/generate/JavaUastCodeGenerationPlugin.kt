// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java.generate

import com.intellij.codeInsight.BlockUtils
import com.intellij.codeInsight.intention.impl.AddOnDemandStaticImportAction
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.VariableKind
import com.intellij.psi.impl.PsiDiamondTypeUtil
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.ElementType
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl
import com.intellij.psi.util.*
import com.intellij.util.asSafely
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

    return JavaCodeStyleManager.getInstance(methodCall.project).shortenClassReferences(methodCall) as PsiMethodCallExpression
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
      updOldPsi is PsiStatement && newPsi is PsiExpression -> factory.createExpressionStatement(newPsi) ?: return null
      updOldPsi is PsiCodeBlock && newPsi is PsiBlockStatement -> newPsi.codeBlock
      else -> newPsi
    }
    return when (val replaced = updOldPsi.replace(updNewPsi)) {
      is PsiExpressionStatement -> replaced.expression.toUElementOfExpectedTypes(elementType)
      is PsiMethodCallExpression -> cleanupMethodCall(replaced).toUElementOfExpectedTypes(elementType)
      is PsiReferenceExpression -> {
        JavaCodeStyleManager.getInstance(replaced.project).shortenClassReferences(replaced).toUElementOfExpectedTypes(elementType)
      }
      else -> replaced.toUElementOfExpectedTypes(elementType)
    }
  }

  override fun bindToElement(reference: UReferenceExpression, element: PsiElement): PsiElement? {
    val sourceReference = reference.sourcePsi ?: return null
    if (sourceReference !is PsiReference) return null
    return sourceReference.bindToElement(element)
  }

  override fun shortenReference(reference: UReferenceExpression): UReferenceExpression? {
    val sourceReference = reference.sourcePsi ?: return null
    val styleManager = JavaCodeStyleManager.getInstance(sourceReference.project)
    return styleManager.shortenClassReferences(sourceReference).toUElementOfType()
  }

  override fun importMemberOnDemand(reference: UQualifiedReferenceExpression): UExpression? {
    val source = reference.sourcePsi ?: return null
    val (qualifier, selector) = when (source) {
      is PsiMethodCallExpression -> source.methodExpression.qualifierExpression to source.methodExpression
      is PsiReferenceExpression -> source.qualifier to source.referenceNameElement
      else -> return null
    }
    if (qualifier == null || selector == null) return null
    val ptr = SmartPointerManager.createPointer(selector)
    val qualifierIdentifier = qualifier.childrenOfType<PsiIdentifier>().firstOrNull() ?: return null
    AddOnDemandStaticImportAction.invoke(source.project, source.containingFile, null, qualifierIdentifier)
    return ptr.element?.parent.toUElementOfType()
  }

  override fun initializeField(uField: UField, uParameter: UParameter): UExpression? {
    val uMethod = uParameter.getParentOfType(UMethod::class.java, false) ?: return null
    val psiMethod = uMethod.sourcePsi as? PsiMethod ?: return null
    val body = psiMethod.body ?: return null

    val elementFactory = JavaPsiFacade.getInstance(psiMethod.project).elementFactory
    val prefix = if (uField.name == uParameter.name) "this." else ""
    val statement = elementFactory.createStatementFromText("$prefix${uField.name} = ${uParameter.name};", psiMethod)
    val lastBodyElement = body.lastBodyElement
    if (lastBodyElement is PsiWhiteSpace) {
      lastBodyElement.replace(statement)
    }
    else {
      body.add(statement)
    }
    return statement.toUElementOfType()
  }
}

private fun PsiElementFactory.createExpressionStatement(expression: PsiExpression): PsiStatement? {
  val statement = createStatementFromText("x;", null) as? PsiExpressionStatement ?: return null
  statement.expression.replace(expression)
  return statement
}

class JavaUastElementFactory(private val project: Project) : UastElementFactory {
  private val psiFactory: PsiElementFactory = JavaPsiFacade.getElementFactory(project)

  override fun createQualifiedReference(qualifiedName: String, context: PsiElement?): UQualifiedReferenceExpression? {
    return psiFactory.createExpressionFromText(qualifiedName, context)
      .asSafely<PsiReferenceExpression>()
      ?.let { JavaUQualifiedReferenceExpression(it, null) }
  }

  override fun createIfExpression(condition: UExpression,
                                  thenBranch: UExpression,
                                  elseBranch: UExpression?,
                                  context: PsiElement?): UIfExpression? {
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
      createCallExpressionTemplateRespectingChainStyle(receiver), context
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
      methodCall.toUElementOfType()
    else
      MethodCallUpgradeHelper(project, methodCall, expectedReturnType).tryUpgradeToExpectedType()
        ?.let { JavaUCallExpression(it, null) }
  }

  private fun createCallExpressionTemplateRespectingChainStyle(receiver: UExpression?): String {
    if (receiver == null) return "a()"
    val siblings = receiver.sourcePsi?.siblings(withSelf = false) ?: return "a.b()"

    (siblings.firstOrNull() as? PsiWhiteSpace)?.let { whitespace ->
      return "a${whitespace.text}.b()"
    }

    if (siblings.firstOrNull()?.elementType == ElementType.DOT) {
      (siblings.elementAt(2) as? PsiWhiteSpace)?.let { whitespace ->
        return "a.${whitespace.text}b()"
      }
    }
    return "a.b()"
  }

  override fun createCallableReferenceExpression(
    receiver: UExpression?,
    methodName: String,
    context: PsiElement?
  ): UCallableReferenceExpression? {
    val receiverSource = receiver?.sourcePsi
    requireNotNull(receiverSource) { "Receiver should not be null for Java callable references." }
    val callableExpression = psiFactory.createExpressionFromText("${receiverSource.text}::$methodName", context)
    if (callableExpression !is PsiMethodReferenceExpression) return null
    return JavaUCallableReferenceExpression(callableExpression, null)
  }

  override fun createStringLiteralExpression(text: String, context: PsiElement?): ULiteralExpression? {
    val literalExpr = psiFactory.createExpressionFromText(StringUtil.wrapWithDoubleQuote(text), context)
    if (literalExpr !is PsiLiteralExpressionImpl) return null
    return JavaULiteralExpression(literalExpr, null)
  }

  override fun createLongConstantExpression(long: Long, context: PsiElement?): UExpression? {
    return when (val literalExpr = psiFactory.createExpressionFromText(long.toString() + "L", context)) {
      is PsiLiteralExpressionImpl -> JavaULiteralExpression(literalExpr, null)
      is PsiPrefixExpression -> JavaUPrefixExpression(literalExpr, null)
      else -> null
    }
  }

  override fun createNullLiteral(context: PsiElement?): ULiteralExpression? {
    val literalExpr = psiFactory.createExpressionFromText("null", context)
    if (literalExpr !is PsiLiteralExpressionImpl) return null
    return JavaULiteralExpression(literalExpr, null)
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
      val expectedTypeTypeParameters = expectedReturnType.asSafely<PsiClassType>()?.parameters ?: return null
      val resultTypeTypeParameters = resultType.asSafely<PsiClassType>()?.parameters ?: return null
      val notEqualTypeParametersIndices = expectedTypeTypeParameters
        .zip(resultTypeTypeParameters)
        .withIndex()
        .filterNot { (_, pair) -> PsiTypesUtil.compareTypes(pair.first, pair.second, false) }
        .map { (i, _) -> i }

      val resolvedMethod = methodCall.resolveMethod() ?: return null
      val methodReturnTypeTypeParameters = (resolvedMethod.returnType.asSafely<PsiClassType>())?.parameters ?: return null
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
      val resolvedThis = this.asSafely<PsiClassType>()?.resolve() ?: return false
      val resolvedOther = other.asSafely<PsiClassType>()?.resolve() ?: return false

      return PsiManager.getInstance(project).areElementsEquivalent(resolvedThis, resolvedOther)
    }
  }

  override fun createDeclarationExpression(declarations: List<UDeclaration>, context: PsiElement?): UDeclarationsExpression {
    return JavaUDeclarationsExpression(null, declarations)
  }

  override fun createReturnExpresion(expression: UExpression?,
                                     inLambda: Boolean,
                                     context: PsiElement?): UReturnExpression? {
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

  override fun createLocalVariable(suggestedName: String?,
                                   type: PsiType?,
                                   initializer: UExpression,
                                   immutable: Boolean,
                                   context: PsiElement?): ULocalVariable? {
    val initializerPsi = initializer.sourcePsi as? PsiExpression ?: return null

    val name = createNameFromSuggested(
      suggestedName,
      VariableKind.LOCAL_VARIABLE,
      type,
      initializer = initializerPsi,
      context = initializerPsi
    ) ?: return null
    val variable = (type ?: initializer.getExpressionType())?.let { variableType ->
      psiFactory.createVariableDeclarationStatement(
        name,
        variableType,
        initializerPsi,
        initializerPsi.context
      ).declaredElements.firstOrNull() as? PsiLocalVariable
    } ?: return null

    variable.setMutability(immutable)

    return JavaULocalVariable(variable, null)
  }

  override fun createBlockExpression(expressions: List<UExpression>, context: PsiElement?): UBlockExpression? {
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
        psi as? PsiStatement ?: (psi as? PsiExpression)?.let { psiFactory.createExpressionStatement(it) }
      }?.let { blockStatement.codeBlock.add(it) } ?: return null
    }

    return JavaUBlockExpression(blockStatement, null)
  }


  override fun createLambdaExpression(parameters: List<UParameterInfo>, body: UExpression, context: PsiElement?): ULambdaExpression? {
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

  override fun createParenthesizedExpression(expression: UExpression, context: PsiElement?): UParenthesizedExpression? {
    val parenthesizedExpression = psiFactory.createExpressionFromText("()", null) as? PsiParenthesizedExpression ?: return null
    parenthesizedExpression.children.getOrNull(1)?.replace(expression.sourcePsi ?: return null) ?: return null
    return JavaUParenthesizedExpression(parenthesizedExpression, null)
  }

  override fun createSimpleReference(name: String, context: PsiElement?): USimpleNameReferenceExpression {
    val reference = psiFactory.createExpressionFromText(name, null)
    return JavaUSimpleNameReferenceExpression(reference, name, null)
  }

  override fun createSimpleReference(variable: UVariable, context: PsiElement?): USimpleNameReferenceExpression? {
    return createSimpleReference(variable.name ?: return null, context)
  }

  override fun createBinaryExpression(leftOperand: UExpression,
                                      rightOperand: UExpression,
                                      operator: UastBinaryOperator,
                                      context: PsiElement?): UBinaryExpression? {
    val leftPsi = leftOperand.sourcePsi ?: return null
    val rightPsi = rightOperand.sourcePsi ?: return null

    val operatorSymbol = when (operator) {
      UastBinaryOperator.LOGICAL_AND -> "&&"
      UastBinaryOperator.PLUS -> "+"
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
                                          operator: UastBinaryOperator,
                                          context: PsiElement?): UPolyadicExpression? {
    val binaryExpression = createBinaryExpression(leftOperand, rightOperand, operator, context) ?: return null
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

  override fun createMethodFromText(methodText: String, context: PsiElement?): UMethod? =
    psiFactory.createMethodFromText(methodText, context).toUElementOfType()

  private val PsiElement.branchStatement: PsiStatement?
    get() = when (this) {
      is PsiExpression -> JavaPsiFacade.getElementFactory(project).createExpressionStatement(this)
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