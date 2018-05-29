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
package org.jetbrains.uast.evaluation

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.*
import org.jetbrains.uast.values.*
import org.jetbrains.uast.values.UNothingValue.JumpKind.BREAK
import org.jetbrains.uast.values.UNothingValue.JumpKind.CONTINUE
import org.jetbrains.uast.visitor.UastTypedVisitor

class TreeBasedEvaluator(
  override val context: UastContext,
  val extensions: List<UEvaluatorExtension>
) : UastTypedVisitor<UEvaluationState, UEvaluationInfo>, UEvaluator {

  override fun getDependents(dependency: UDependency): Set<UValue> {
    return resultCache.values.map { it.value }.filter { dependency in it.dependencies }.toSet()
  }

  private val inputStateCache = mutableMapOf<UExpression, UEvaluationState>()

  private val resultCache = mutableMapOf<UExpression, UEvaluationInfo>()

  override fun visitElement(node: UElement, data: UEvaluationState): UEvaluationInfo {
    return UEvaluationInfo(UUndeterminedValue, data).apply {
      if (node is UExpression) {
        this storeResultFor node
      }
    }
  }

  override fun analyze(method: UMethod, state: UEvaluationState) {
    method.uastBody?.accept(this, state)
  }

  override fun analyze(field: UField, state: UEvaluationState) {
    field.uastInitializer?.accept(this, state)
  }

  internal fun getCached(expression: UExpression): UValue? {
    return resultCache[expression]?.value
  }

  override fun evaluate(expression: UExpression, state: UEvaluationState?): UValue {
    if (state == null) {
      val result = resultCache[expression]
      if (result != null) return result.value
    }
    val inputState = state ?: inputStateCache[expression] ?: expression.createEmptyState()
    return expression.accept(this, inputState).value
  }

  // ----------------------- //

  private infix fun UValue.to(state: UEvaluationState) = UEvaluationInfo(this, state)

  private infix fun UEvaluationInfo.storeResultFor(expression: UExpression) = apply {
    resultCache[expression] = this
  }

  override fun visitLiteralExpression(node: ULiteralExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data
    val value = node.value
    return value.toConstant(node) to data storeResultFor node
  }

  override fun visitClassLiteralExpression(node: UClassLiteralExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data
    return (node.type?.let { value -> UClassConstant(value, node) } ?: UUndeterminedValue) to data storeResultFor node
  }

  override fun visitReturnExpression(node: UReturnExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data
    val argument = node.returnExpression
    return UValue.UNREACHABLE to (argument?.accept(this, data)?.state ?: data) storeResultFor node
  }

  override fun visitBreakExpression(node: UBreakExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data
    return UNothingValue(node) to data storeResultFor node
  }

  override fun visitContinueExpression(node: UContinueExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data
    return UNothingValue(node) to data storeResultFor node
  }

  override fun visitThrowExpression(node: UThrowExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data
    return UValue.UNREACHABLE to data storeResultFor node
  }
  // ----------------------- //

  override fun visitSimpleNameReferenceExpression(
    node: USimpleNameReferenceExpression,
    data: UEvaluationState
  ): UEvaluationInfo {
    inputStateCache[node] = data
    val resolvedElement = node.resolveToUElement()
    return when (resolvedElement) {
      is UEnumConstant -> UEnumEntryValueConstant(resolvedElement, node)
      is UField -> if (resolvedElement.hasModifierProperty(PsiModifier.FINAL)) {
        data[resolvedElement].ifUndetermined {
          val helper = JavaPsiFacade.getInstance(resolvedElement.project).constantEvaluationHelper
          val evaluated = helper.computeConstantExpression(resolvedElement.initializer)
          evaluated?.toConstant() ?: UUndeterminedValue
        }
      }
      else {
        return super.visitSimpleNameReferenceExpression(node, data)
      }
      is UVariable -> data[resolvedElement].ifUndetermined {
        node.evaluateViaExtensions { evaluateVariable(resolvedElement, data) }?.value ?: UUndeterminedValue
      }
      else -> return super.visitSimpleNameReferenceExpression(node, data)
    } to data storeResultFor node
  }

  override fun visitReferenceExpression(
    node: UReferenceExpression,
    data: UEvaluationState
  ): UEvaluationInfo {
    inputStateCache[node] = data
    return UCallResultValue(node, emptyList()) to data storeResultFor node
  }

  // ----------------------- //

  private fun UExpression.assign(
    valueInfo: UEvaluationInfo,
    operator: UastBinaryOperator.AssignOperator = UastBinaryOperator.ASSIGN
  ): UEvaluationInfo {
    this.accept(this@TreeBasedEvaluator, valueInfo.state)
    if (this is UResolvable) {
      val resolvedElement = resolve()
      if (resolvedElement is PsiVariable) {
        val variable = context.getVariable(resolvedElement)
        val currentValue = valueInfo.state[variable]
        val result = when (operator) {
          UastBinaryOperator.ASSIGN -> valueInfo.value
          UastBinaryOperator.PLUS_ASSIGN -> currentValue + valueInfo.value
          UastBinaryOperator.MINUS_ASSIGN -> currentValue - valueInfo.value
          UastBinaryOperator.MULTIPLY_ASSIGN -> currentValue * valueInfo.value
          UastBinaryOperator.DIVIDE_ASSIGN -> currentValue / valueInfo.value
          UastBinaryOperator.REMAINDER_ASSIGN -> currentValue % valueInfo.value
          UastBinaryOperator.AND_ASSIGN -> currentValue bitwiseAnd valueInfo.value
          UastBinaryOperator.OR_ASSIGN -> currentValue bitwiseOr valueInfo.value
          UastBinaryOperator.XOR_ASSIGN -> currentValue bitwiseXor valueInfo.value
          UastBinaryOperator.SHIFT_LEFT_ASSIGN -> currentValue shl valueInfo.value
          UastBinaryOperator.SHIFT_RIGHT_ASSIGN -> currentValue shr valueInfo.value
          UastBinaryOperator.UNSIGNED_SHIFT_RIGHT_ASSIGN -> currentValue ushr valueInfo.value
          else -> UUndeterminedValue
        }
        return result to valueInfo.state.assign(variable, result, this)
      }
    }
    return UUndeterminedValue to valueInfo.state
  }

  private fun UExpression.assign(
    operator: UastBinaryOperator.AssignOperator,
    value: UExpression,
    data: UEvaluationState
  ) = assign(value.accept(this@TreeBasedEvaluator, data), operator)

  override fun visitPrefixExpression(node: UPrefixExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data
    val operandInfo = node.operand.accept(this, data)
    val operandValue = operandInfo.value
    if (!operandValue.reachable) return operandInfo storeResultFor node
    return when (node.operator) {
      UastPrefixOperator.UNARY_PLUS -> operandValue
      UastPrefixOperator.UNARY_MINUS -> -operandValue
      UastPrefixOperator.LOGICAL_NOT -> !operandValue
      UastPrefixOperator.INC -> {
        val resultValue = operandValue.inc()
        val newState = node.operand.assign(resultValue to operandInfo.state).state
        return resultValue to newState storeResultFor node
      }
      UastPrefixOperator.DEC -> {
        val resultValue = operandValue.dec()
        val newState = node.operand.assign(resultValue to operandInfo.state).state
        return resultValue to newState storeResultFor node
      }
      else -> {
        return node.evaluateViaExtensions { evaluatePrefix(node.operator, operandValue, operandInfo.state) }
               ?: UUndeterminedValue to operandInfo.state storeResultFor node
      }
    } to operandInfo.state storeResultFor node
  }

  inline fun UElement.evaluateViaExtensions(block: UEvaluatorExtension.() -> UEvaluationInfo): UEvaluationInfo? {
    for (ext in extensions) {
      val extResult = ext.block()
      if (extResult.value != UUndeterminedValue) return extResult
    }
    languageExtension()?.block()?.let { if (it.value != UUndeterminedValue) return it }
    return null
  }

  override fun visitPostfixExpression(node: UPostfixExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data
    val operandInfo = node.operand.accept(this, data)
    val operandValue = operandInfo.value
    if (!operandValue.reachable) return operandInfo storeResultFor node
    return when (node.operator) {
      UastPostfixOperator.INC -> {
        operandValue to node.operand.assign(operandValue.inc() to operandInfo.state).state
      }
      UastPostfixOperator.DEC -> {
        operandValue to node.operand.assign(operandValue.dec() to operandInfo.state).state
      }
      else -> {
        return node.evaluateViaExtensions { evaluatePostfix(node.operator, operandValue, operandInfo.state) }
               ?: UUndeterminedValue to operandInfo.state storeResultFor node
      }
    } storeResultFor node
  }

  private fun UastBinaryOperator.evaluate(left: UValue, right: UValue): UValue? =
    when (this) {
      UastBinaryOperator.PLUS -> left + right
      UastBinaryOperator.MINUS -> left - right
      UastBinaryOperator.MULTIPLY -> left * right
      UastBinaryOperator.DIV -> left / right
      UastBinaryOperator.MOD -> left % right
      UastBinaryOperator.EQUALS -> left valueEquals right
      UastBinaryOperator.NOT_EQUALS -> left valueNotEquals right
      UastBinaryOperator.IDENTITY_EQUALS -> left identityEquals right
      UastBinaryOperator.IDENTITY_NOT_EQUALS -> left identityNotEquals right
      UastBinaryOperator.GREATER -> left greater right
      UastBinaryOperator.LESS -> left less right
      UastBinaryOperator.GREATER_OR_EQUALS -> left greaterOrEquals right
      UastBinaryOperator.LESS_OR_EQUALS -> left lessOrEquals right
      UastBinaryOperator.LOGICAL_AND -> left and right
      UastBinaryOperator.LOGICAL_OR -> left or right
      UastBinaryOperator.BITWISE_AND -> left bitwiseAnd right
      UastBinaryOperator.BITWISE_OR -> left bitwiseOr right
      UastBinaryOperator.BITWISE_XOR -> left bitwiseXor right
      UastBinaryOperator.SHIFT_LEFT -> left shl right
      UastBinaryOperator.SHIFT_RIGHT -> left shr right
      UastBinaryOperator.UNSIGNED_SHIFT_RIGHT -> left ushr right
      else -> null
    }

  override fun visitBinaryExpression(node: UBinaryExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data
    val operator = node.operator

    if (operator is UastBinaryOperator.AssignOperator) {
      return node.leftOperand.assign(operator, node.rightOperand, data) storeResultFor node
    }

    val leftInfo = node.leftOperand.accept(this, data)
    if (!leftInfo.reachable) {
      return leftInfo storeResultFor node
    }

    val rightInfo = node.rightOperand.accept(this, leftInfo.state)

    operator.evaluate(leftInfo.value, rightInfo.value)?.let {
      return it to rightInfo.state storeResultFor node
    }

    return node.evaluateViaExtensions { evaluateBinary(node, leftInfo.value, rightInfo.value, rightInfo.state) }
           ?: UUndeterminedValue to rightInfo.state storeResultFor node
  }

  override fun visitPolyadicExpression(node: UPolyadicExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data
    val operator = node.operator

    val infos = node.operands.map {
      it.accept(this, data).apply {
        if (!reachable) {
          return this storeResultFor node
        }
      }
    }

    val lastInfo = infos.last()
    val firstValue = infos.first().value
    val restInfos = infos.drop(1)

    return restInfos.fold(firstValue) { accumulator, info ->
      operator.evaluate(accumulator, info.value) ?: return UUndeterminedValue to info.state storeResultFor node
    } to lastInfo.state storeResultFor node
  }

  private fun evaluateTypeCast(operandInfo: UEvaluationInfo, type: PsiType): UEvaluationInfo {
    val constant = operandInfo.value.toConstant() ?: return UUndeterminedValue to operandInfo.state
    val resultConstant = when (type) {
                           PsiType.BOOLEAN -> {
                             constant as? UBooleanConstant
                           }
                           PsiType.CHAR -> when (constant) {
                           // Join the following three in UNumericConstant
                           // when https://youtrack.jetbrains.com/issue/KT-14868 is fixed
                             is UIntConstant -> UCharConstant(constant.value.toChar())
                             is ULongConstant -> UCharConstant(constant.value.toChar())
                             is UFloatConstant -> UCharConstant(constant.value.toChar())

                             is UCharConstant -> constant
                             else -> null
                           }
                           PsiType.LONG -> {
                             (constant as? UNumericConstant)?.value?.toLong()?.let { value -> ULongConstant(value) }
                           }
                           PsiType.BYTE, PsiType.SHORT, PsiType.INT -> {
                             (constant as? UNumericConstant)?.value?.toInt()?.let { UIntConstant(it, type) }
                           }
                           PsiType.FLOAT, PsiType.DOUBLE -> {
                             (constant as? UNumericConstant)?.value?.toDouble()?.let { UFloatConstant.create(it, type) }
                           }
                           else -> when (type.name) {
                             "java.lang.String" -> UStringConstant(constant.asString())
                             else -> null
                           }
                         } ?: return UUndeterminedValue to operandInfo.state
    return when (operandInfo.value) {
      resultConstant -> return operandInfo
      is UConstant -> resultConstant
      is UDependentValue -> UDependentValue.create(resultConstant, operandInfo.value.dependencies)
      else -> UUndeterminedValue
    } to operandInfo.state
  }

  private fun evaluateTypeCheck(operandInfo: UEvaluationInfo, type: PsiType): UEvaluationInfo {
    val constant = operandInfo.value.toConstant() ?: return UUndeterminedValue to operandInfo.state
    val valid = when (type) {
      PsiType.BOOLEAN -> constant is UBooleanConstant
      PsiType.LONG -> constant is ULongConstant
      PsiType.BYTE, PsiType.SHORT, PsiType.INT, PsiType.CHAR -> constant is UIntConstant
      PsiType.FLOAT, PsiType.DOUBLE -> constant is UFloatConstant
      else -> when (type.name) {
        "java.lang.String" -> constant is UStringConstant
        else -> false
      }
    }
    return UBooleanConstant.valueOf(valid) to operandInfo.state
  }

  override fun visitBinaryExpressionWithType(
    node: UBinaryExpressionWithType, data: UEvaluationState
  ): UEvaluationInfo {
    inputStateCache[node] = data
    val operandInfo = node.operand.accept(this, data)
    if (!operandInfo.reachable || operandInfo.value == UUndeterminedValue) {
      return operandInfo storeResultFor node
    }
    return when (node.operationKind) {
      UastBinaryExpressionWithTypeKind.TYPE_CAST -> evaluateTypeCast(operandInfo, node.type)
      UastBinaryExpressionWithTypeKind.INSTANCE_CHECK -> evaluateTypeCheck(operandInfo, node.type)
      else -> UUndeterminedValue to operandInfo.state
    } storeResultFor node
  }

  override fun visitParenthesizedExpression(node: UParenthesizedExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data
    return node.expression.accept(this, data) storeResultFor node
  }

  override fun visitLabeledExpression(node: ULabeledExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data
    return node.expression.accept(this, data) storeResultFor node
  }

  override fun visitCallExpression(node: UCallExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data

    var currentInfo = UUndeterminedValue to data
    currentInfo = node.receiver?.accept(this, currentInfo.state) ?: currentInfo
    if (!currentInfo.reachable) return currentInfo storeResultFor node
    val argumentValues = mutableListOf<UValue>()
    for (valueArgument in node.valueArguments) {
      currentInfo = valueArgument.accept(this, currentInfo.state)
      if (!currentInfo.reachable) return currentInfo storeResultFor node
      argumentValues.add(currentInfo.value)
    }

    return (node.evaluateViaExtensions {
      node.resolve()?.let { method -> evaluateMethodCall(method, argumentValues, currentInfo.state) }
      ?: UUndeterminedValue to currentInfo.state
    } ?: UCallResultValue(node, argumentValues) to currentInfo.state) storeResultFor node
  }

  override fun visitQualifiedReferenceExpression(
    node: UQualifiedReferenceExpression,
    data: UEvaluationState
  ): UEvaluationInfo {
    inputStateCache[node] = data

    var currentInfo = UUndeterminedValue to data
    currentInfo = node.receiver.accept(this, currentInfo.state)
    if (!currentInfo.reachable) return currentInfo storeResultFor node

    val selectorInfo = node.selector.accept(this, currentInfo.state)
    return when (node.accessType) {
      UastQualifiedExpressionAccessType.SIMPLE -> {
        selectorInfo
      }
      else -> {
        return node.evaluateViaExtensions { evaluateQualified(node.accessType, currentInfo, selectorInfo) }
               ?: UUndeterminedValue to selectorInfo.state storeResultFor node
      }
    } storeResultFor node
  }

  override fun visitDeclarationsExpression(
    node: UDeclarationsExpression,
    data: UEvaluationState
  ): UEvaluationInfo {
    inputStateCache[node] = data
    var currentInfo = UUndeterminedValue to data
    for (variable in node.declarations) {
      currentInfo = variable.accept(this, currentInfo.state)
      if (!currentInfo.reachable) return currentInfo storeResultFor node
    }
    return currentInfo storeResultFor node
  }

  override fun visitVariable(node: UVariable, data: UEvaluationState): UEvaluationInfo {
    val initializer = node.uastInitializer
    val initializerInfo = initializer?.accept(this, data) ?: UUndeterminedValue to data
    if (!initializerInfo.reachable) return initializerInfo
    return UUndeterminedValue to initializerInfo.state.assign(node, initializerInfo.value, node)
  }

  // ----------------------- //

  override fun visitBlockExpression(node: UBlockExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data
    var currentInfo = UUndeterminedValue to data
    for (expression in node.expressions) {
      currentInfo = expression.accept(this, currentInfo.state)
      if (!currentInfo.reachable) return currentInfo storeResultFor node
    }
    return currentInfo storeResultFor node
  }

  override fun visitIfExpression(node: UIfExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data
    val conditionInfo = node.condition.accept(this, data)
    if (!conditionInfo.reachable) return conditionInfo storeResultFor node

    val thenInfo = node.thenExpression?.accept(this, conditionInfo.state)
    val elseInfo = node.elseExpression?.accept(this, conditionInfo.state)
    val conditionValue = conditionInfo.value
    val defaultInfo = UUndeterminedValue to conditionInfo.state
    val constantConditionValue = conditionValue.toConstant()

    return when (constantConditionValue) {
      is UBooleanConstant -> {
        if (constantConditionValue.value) thenInfo ?: defaultInfo
        else elseInfo ?: defaultInfo
      }
      else -> when {
        thenInfo == null -> elseInfo?.merge(defaultInfo) ?: defaultInfo
        elseInfo == null -> thenInfo.merge(defaultInfo)
        else -> thenInfo.merge(elseInfo)
      }
    } storeResultFor node
  }

  override fun visitSwitchExpression(node: USwitchExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data
    val subjectInfo = node.expression?.accept(this, data) ?: UUndeterminedValue to data
    if (!subjectInfo.reachable) return subjectInfo storeResultFor node

    var resultInfo: UEvaluationInfo? = null
    var clauseInfo = subjectInfo
    var fallThroughCondition: UValue = UBooleanConstant.False

    fun List<UExpression>.evaluateAndFold(): UValue =
      this.map {
        clauseInfo = it.accept(this@TreeBasedEvaluator, clauseInfo.state)
        (clauseInfo.value valueEquals subjectInfo.value).toConstant() as? UValueBase ?: UUndeterminedValue
      }.fold(UBooleanConstant.False) { previous: UValue, next -> previous or next }

    clausesLoop@ for (expression in node.body.expressions) {
      val switchClauseWithBody = expression as USwitchClauseExpressionWithBody
      val caseCondition = switchClauseWithBody.caseValues.evaluateAndFold().or(fallThroughCondition)

      if (caseCondition != UBooleanConstant.False) {
        for (bodyExpression in switchClauseWithBody.body.expressions) {
          clauseInfo = bodyExpression.accept(this, clauseInfo.state)
          if (!clauseInfo.reachable) break
        }
        val clauseValue = clauseInfo.value
        if (clauseValue is UNothingValue && clauseValue.containingLoopOrSwitch == node) {
          // break from switch
          resultInfo = resultInfo?.merge(clauseInfo) ?: clauseInfo
          if (caseCondition == UBooleanConstant.True) break@clausesLoop
          clauseInfo = subjectInfo
          fallThroughCondition = UBooleanConstant.False
        }
        // TODO: jump out
        else {
          fallThroughCondition = caseCondition
          clauseInfo = clauseInfo.merge(subjectInfo)
        }
      }
    }

    resultInfo = resultInfo ?: subjectInfo
    val resultValue = resultInfo.value
    if (resultValue is UNothingValue && resultValue.containingLoopOrSwitch == node) {
      resultInfo = resultInfo.copy(UUndeterminedValue)
    }
    return resultInfo storeResultFor node
  }

  private fun evaluateLoop(
    loop: ULoopExpression,
    inputState: UEvaluationState,
    condition: UExpression? = null,
    infinite: Boolean = false,
    update: UExpression? = null
  ): UEvaluationInfo {

    fun evaluateCondition(inputState: UEvaluationState): UEvaluationInfo =
      condition?.accept(this, inputState)
      ?: (if (infinite) UBooleanConstant.True else UUndeterminedValue) to inputState

    ProgressManager.checkCanceled()

    var resultInfo = UUndeterminedValue to inputState
    do {
      val previousInfo = resultInfo
      resultInfo = evaluateCondition(resultInfo.state)
      val conditionConstant = resultInfo.value.toConstant()
      if (conditionConstant == UBooleanConstant.False) {
        return resultInfo.copy(UUndeterminedValue) storeResultFor loop
      }
      val bodyInfo = loop.body.accept(this, resultInfo.state)
      val bodyValue = bodyInfo.value
      if (bodyValue is UNothingValue) {
        if (bodyValue.kind == BREAK && bodyValue.containingLoopOrSwitch == loop) {
          return if (conditionConstant == UBooleanConstant.True) {
            bodyInfo.copy(UUndeterminedValue)
          }
          else {
            bodyInfo.copy(UUndeterminedValue).merge(previousInfo)
          } storeResultFor loop
        }
        else if (bodyValue.kind == CONTINUE && bodyValue.containingLoopOrSwitch == loop) {
          val updateInfo = update?.accept(this, bodyInfo.state) ?: bodyInfo
          resultInfo = updateInfo.copy(UUndeterminedValue).merge(previousInfo)
        }
        else {
          return if (conditionConstant == UBooleanConstant.True) {
            bodyInfo
          }
          else {
            resultInfo.copy(UUndeterminedValue)
          } storeResultFor loop
        }
      }
      else {
        val updateInfo = update?.accept(this, bodyInfo.state) ?: bodyInfo
        resultInfo = updateInfo.merge(previousInfo)
      }
    }
    while (previousInfo != resultInfo)
    return resultInfo.copy(UUndeterminedValue) storeResultFor loop
  }

  override fun visitForEachExpression(node: UForEachExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data
    val iterableInfo = node.iteratedValue.accept(this, data)
    return evaluateLoop(node, iterableInfo.state)
  }

  override fun visitForExpression(node: UForExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data
    val initialState = node.declaration?.accept(this, data)?.state ?: data
    return evaluateLoop(node, initialState, node.condition, node.condition == null, node.update)
  }

  override fun visitWhileExpression(node: UWhileExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data
    return evaluateLoop(node, data, node.condition)
  }

  override fun visitDoWhileExpression(node: UDoWhileExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data
    val bodyInfo = node.body.accept(this, data)
    return evaluateLoop(node, bodyInfo.state, node.condition)
  }

  override fun visitTryExpression(node: UTryExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data
    val tryInfo = node.tryClause.accept(this, data)
    val mergedTryInfo = tryInfo.merge(UUndeterminedValue to data)
    val catchInfoList = node.catchClauses.map { it.accept(this, mergedTryInfo.state) }
    val mergedTryCatchInfo = catchInfoList.fold(mergedTryInfo, UEvaluationInfo::merge)
    val finallyInfo = node.finallyClause?.accept(this, mergedTryCatchInfo.state) ?: mergedTryCatchInfo
    return finallyInfo storeResultFor node
  }

  // ----------------------- //

  override fun visitObjectLiteralExpression(node: UObjectLiteralExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data
    val objectInfo = node.declaration.accept(this, data)
    val resultState = data.merge(objectInfo.state)
    return UUndeterminedValue to resultState storeResultFor node
  }

  override fun visitLambdaExpression(node: ULambdaExpression, data: UEvaluationState): UEvaluationInfo {
    inputStateCache[node] = data
    val lambdaInfo = node.body.accept(this, data)
    val resultState = data.merge(lambdaInfo.state)
    return UUndeterminedValue to resultState storeResultFor node
  }

  override fun visitClass(node: UClass, data: UEvaluationState): UEvaluationInfo {
    // fields / initializers / nested classes?
    var resultState = data
    for (method in node.methods) {
      resultState = resultState.merge(method.accept(this, resultState).state)
    }
    return UUndeterminedValue to resultState
  }

  override fun visitMethod(node: UMethod, data: UEvaluationState): UEvaluationInfo {
    return UUndeterminedValue to (node.uastBody?.accept(this, data)?.state ?: data)
  }
}

fun Any?.toConstant(node: ULiteralExpression? = null): UValueBase = when (this) {
  null -> UNullConstant
  is Float -> UFloatConstant.create(this.toDouble(), UNumericType.FLOAT, node)
  is Double -> UFloatConstant.create(this, UNumericType.DOUBLE, node)
  is Long -> ULongConstant(this, node)
  is Int -> UIntConstant(this, UNumericType.INT, node)
  is Short -> UIntConstant(this.toInt(), UNumericType.SHORT, node)
  is Byte -> UIntConstant(this.toInt(), UNumericType.BYTE, node)
  is Char -> UCharConstant(this, node)
  is Boolean -> UBooleanConstant.valueOf(this)
  is String -> UStringConstant(this, node)
  else -> UUndeterminedValue
}
