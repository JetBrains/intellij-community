// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion

import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.codeInsight.completion.ml.ContextFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.jetbrains.python.codeInsight.mlcompletion.prev2calls.PrevCallsModelsProviderService
import com.jetbrains.python.codeInsight.mlcompletion.prev2calls.PyPrevCallsCompletionFeatures
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.types.TypeEvalContext

class PyContextFeatureProvider : ContextFeatureProvider {
  override fun getName(): String = "python"

  override fun calculateFeatures(environment: CompletionEnvironment): Map<String, MLFeatureValue> {
    val result = HashMap<String, MLFeatureValue>()
    val position = environment.parameters.position
    val typeEvalContext = TypeEvalContext.codeInsightFallback(position.project)

    result["is_in_condition"] = MLFeatureValue.binary(PyCompletionFeatures.isInCondition(position))
    result["is_after_if_statement_without_else_branch"] = MLFeatureValue.binary(PyCompletionFeatures.isAfterIfStatementWithoutElseBranch(position))
    result["is_in_for_statement"] = MLFeatureValue.binary(PyCompletionFeatures.isInForStatement(position))

    val positionParent = position.parent
    if (positionParent is PyExpression) {
      result["num_of_prev_qualifiers"] = MLFeatureValue.numerical(PyMlCompletionHelpers.getQualifiedComponents(positionParent).size)
    }

    val neighboursKws = PyCompletionFeatures.getPrevNeighboursKeywordIds(position)
    if (neighboursKws.size > 0) result["prev_neighbour_keyword_1"] = MLFeatureValue.numerical(neighboursKws[0])
    if (neighboursKws.size > 1) result["prev_neighbour_keyword_2"] = MLFeatureValue.numerical(neighboursKws[1])

    val sameLineKws = PyCompletionFeatures.getPrevKeywordsIdsInTheSameLine(position)
    if (sameLineKws.size > 0) result["prev_same_line_keyword_1"] = MLFeatureValue.numerical(sameLineKws[0])
    if (sameLineKws.size > 1) result["prev_same_line_keyword_2"] = MLFeatureValue.numerical(sameLineKws[1])

    val sameColumnKws = PyCompletionFeatures.getPrevKeywordsIdsInTheSameColumn(position)
    if (sameColumnKws.size > 0) result["prev_same_column_keyword_1"] = MLFeatureValue.numerical(sameColumnKws[0])
    if (sameColumnKws.size > 1) result["prev_same_column_keyword_2"] = MLFeatureValue.numerical(sameColumnKws[1])

    with (PyArgumentsCompletionFeatures.getContextArgumentFeatures(position)) {
      result["is_in_arguments"] = MLFeatureValue.binary(isInArguments)
      result["is_directly_in_arguments_context"] = MLFeatureValue.binary(isDirectlyInArgumentContext)
      result["is_into_keyword_arg"] = MLFeatureValue.binary(isIntoKeywordArgument)
      result["have_named_arg_left"] = MLFeatureValue.binary(haveNamedArgLeft)
      result["have_named_arg_right"] = MLFeatureValue.binary(haveNamedArgRight)
      argumentIndex?.let { result["argument_index"] = MLFeatureValue.numerical(it) }
      argumentsSize?.let { result["number_of_arguments_already"] = MLFeatureValue.numerical(it) }
    }

    PyReceiverMlCompletionFeatures.calculateReceiverElementInfo(environment, typeEvalContext)
    PyNamesMatchingMlCompletionFeatures.calculateFunBodyNames(environment)
    PyNamesMatchingMlCompletionFeatures.calculateNamedArgumentsNames(environment)
    PyNamesMatchingMlCompletionFeatures.calculateImportNames(environment)
    PyNamesMatchingMlCompletionFeatures.calculateStatementListNames(environment)
    PyNamesMatchingMlCompletionFeatures.calculateEnclosingMethodName(environment)

    PyNamesMatchingMlCompletionFeatures.calculateSameLineLeftNames(environment).let { names ->
      result["have_opening_round_bracket"] = MLFeatureValue.binary(PyParenthesesFeatures.haveOpeningRoundBracket(names))
      result["have_opening_square_bracket"] = MLFeatureValue.binary(PyParenthesesFeatures.haveOpeningSquareBracket(names))
      result["have_opening_brace"] = MLFeatureValue.binary(PyParenthesesFeatures.haveOpeningBrace(names))
    }

    PyClassCompletionFeatures.getClassCompletionFeatures(environment)?.let { with(it) {
      result["diff_lines_with_class_def"] = MLFeatureValue.numerical(diffLinesWithClassDef)
      result["containing_class_have_constructor"] = MLFeatureValue.binary(classHaveConstructor)
    }}

    val cursorOffset = environment.lookup.lookupStart
    val isInCondition = result["is_in_condition"]?.value as? Boolean ?: false
    val isInForStatement = result["is_in_for_statement"]?.value as? Boolean ?: false
    PyPrevCallsCompletionFeatures.calculatePrevCallsContextInfo(cursorOffset, position, isInCondition, isInForStatement)?.let {
      PrevCallsModelsProviderService.instance.loadModelFor(it.qualifier)
      environment.putUserData(PyPrevCallsCompletionFeatures.PREV_CALLS_CONTEXT_INFO_KEY, it)
    }

    return result
  }
}