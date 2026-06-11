// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.google.common.collect.Sets
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.psi.PsiElement
import com.intellij.xml.util.XmlStringUtil
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.typing.matchingProtocolDefinitions
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.inspections.PyTypeCheckerInspection.AnalyzeArgumentResult
import com.jetbrains.python.inspections.PyTypeCheckerInspection.AnalyzeCalleeResults
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyCallSiteOwner
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.impl.PyPsiUtils.getFirstChildOfType
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyStructuralType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import java.util.Optional

internal object PyTypeCheckerInspectionProblemRegistrar {
  fun registerProblem(
    visitor: PyInspectionVisitor,
    callSite: PyCallSiteOwner,
    argumentTypes: List<PyType?>,
    calleesResults: List<AnalyzeCalleeResults>,
    context: TypeEvalContext,
    highlightOverride: ProblemHighlightType?,
  ) {
    if (calleesResults.size == 1) {
      registerSingleCalleeProblem(
        visitor,
        callSite,
        calleesResults[0],
        context,
        highlightOverride
      )
    }
    else if (!calleesResults.isEmpty()) {
      registerMultiCalleeProblem(visitor, callSite, argumentTypes, calleesResults, context, highlightOverride)
    }
  }

  private fun registerSingleCalleeProblem(
    visitor: PyInspectionVisitor,
    callSite: PyCallSiteOwner,
    calleeResults: AnalyzeCalleeResults,
    context: TypeEvalContext,
    highlightOverride: ProblemHighlightType?,
  ) {
    for (argumentResult in calleeResults.results) {
      if (argumentResult.isMatched) continue

      registerWithOverride(
        visitor,
        argumentResult.argument,
        getSingleCalleeProblemMessage(argumentResult, context),
        highlightOverride
      )
    }

    for (unexpectedArgumentForParamSpec in calleeResults.unmatchedArguments) {
      val argument = unexpectedArgumentForParamSpec.argument
      val paramSpecTypeName = unexpectedArgumentForParamSpec.paramSpecType.variableName
      registerWithOverride(
        visitor, argument,
        PyPsiBundle.problemMessage("INSP.type.checker.unexpected.argument.from.paramspec", paramSpecTypeName),
        highlightOverride
      )
    }

    if (callSite is PyCallExpression) {
      val argumentList = callSite.argumentList
      if (argumentList != null) {
        val rpar = getFirstChildOfType(argumentList, PyTokenTypes.RPAR)
        if (rpar != null) {
          for (unfilledParameterFromParamSpec in calleeResults.unmatchedParameters) {
            val parameterName = unfilledParameterFromParamSpec.parameter.name
            val paramSpecTypeName = unfilledParameterFromParamSpec.paramSpecType.variableName
            if (parameterName != null) {
              registerWithOverride(
                visitor, rpar, PyPsiBundle.problemMessage(
                  "INSP.type.checker.unfilled.parameter.for.paramspec", parameterName,
                  paramSpecTypeName
                ), highlightOverride
              )
            }
          }

          for (unfilledParameterFromParamSpec in calleeResults.unfilledPositionalVarargs) {
            val varargName = unfilledParameterFromParamSpec.varargName
            val expectedTypes = unfilledParameterFromParamSpec.expectedTypes
            registerWithOverride(
              visitor, rpar, PyPsiBundle.problemMessage("INSP.type.checker.unfilled.vararg", varargName, expectedTypes),
              highlightOverride
            )
          }
        }
      }
    }
  }

  private fun registerWithOverride(
    visitor: PyInspectionVisitor,
    element: PsiElement,
    @InspectionMessage message: @InspectionMessage String,
    highlightOverride: ProblemHighlightType?,
  ) {
    if (highlightOverride != null) {
      visitor.registerProblem(element, message, highlightOverride)
    }
    else {
      visitor.registerProblem(element, message)
    }
  }

  private fun registerWithOverride(
    visitor: PyInspectionVisitor,
    element: PsiElement,
    message: PyInspectionMessages.ProblemMessage,
    highlightOverride: ProblemHighlightType?,
  ) {
    val type = highlightOverride ?: ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    visitor.registerProblem(element, message, type)
  }

  private fun registerMultiCalleeProblem(
    visitor: PyInspectionVisitor,
    callSite: PyCallSiteOwner,
    argumentTypes: List<PyType?>,
    calleesResults: List<AnalyzeCalleeResults>,
    context: TypeEvalContext,
    highlightOverride: ProblemHighlightType?,
  ) {
    if (callSite is PyBinaryExpression) {
      registerMultiCalleeProblemForBinaryExpression(
        visitor, callSite, argumentTypes, calleesResults, context,
        highlightOverride
      )
    }
    else {
      registerWithOverride(
        visitor, getMultiCalleeElementToHighlight(callSite),
        getMultiCalleeProblemMessage(argumentTypes, calleesResults, context, isOnTheFly(visitor)), highlightOverride
      )
    }
  }

  private fun getSingleCalleeProblemMessage(
    argumentResult: AnalyzeArgumentResult,
    context: TypeEvalContext,
  ): PyInspectionMessages.ProblemMessage {
    val actualType = argumentResult.actualType
    val expectedType = argumentResult.expectedType

    checkNotNull(actualType) // see PyTypeCheckerInspection.Visitor.analyzeArgument()
    checkNotNull(expectedType) // see PyTypeCheckerInspection.Visitor.analyzeArgument()

    val actualTypeName = PythonDocumentationProvider.getTypeName(actualType, context)

    if (expectedType is PyStructuralType) {
      val expectedAttributes = expectedType.attributeNames
      val actualAttributes = getAttributes(actualType, context)

      if (actualAttributes != null) {
        val missingAttributes = Sets.difference<String?>(expectedAttributes, actualAttributes)
        return PyPsiBundle.problemMessage(
          "INSP.type.checker.type.does.not.have.expected.attribute",
          actualTypeName, missingAttributes.size,
          PyInspectionMessages.CodifiedParam.joinNames(missingAttributes.filterNotNull())
        )
      }
    }

    val expectedTypeAfterSubstitution = argumentResult.expectedTypeAfterSubstitution
    val expectedTypeName = PythonDocumentationProvider.getVerboseTypeName(expectedType, context)
    val expectedSubstitutedName = if (expectedTypeAfterSubstitution != null && expectedTypeAfterSubstitution != expectedType)
      PythonDocumentationProvider.getTypeName(expectedTypeAfterSubstitution, context)
    else
      null

    if (matchingProtocolDefinitions(expectedType, actualType, context)) {
      if (expectedSubstitutedName != null) {
        return PyPsiBundle.problemMessage(
          "INSP.type.checker.only.concrete.class.can.be.used.where.matched.protocol.expected",
          expectedSubstitutedName, expectedTypeName
        )
      }
      else {
        return PyPsiBundle.problemMessage("INSP.type.checker.only.concrete.class.can.be.used.where.protocol.expected", expectedTypeName)
      }
    }

    if (expectedSubstitutedName != null) {
      return PyPsiBundle.problemMessage(
        "INSP.type.checker.expected.matched.type.got.type.instead", expectedSubstitutedName, expectedTypeName,
        actualTypeName
      )
    }
    else {
      return PyPsiBundle.problemMessage("INSP.type.checker.expected.type.got.type.instead", expectedTypeName, actualTypeName)
    }
  }

  private fun registerMultiCalleeProblemForBinaryExpression(
    visitor: PyInspectionVisitor,
    binaryExpression: PyBinaryExpression,
    argumentTypes: List<PyType?>,
    calleesResults: List<AnalyzeCalleeResults>,
    context: TypeEvalContext,
    highlightOverride: ProblemHighlightType?,
  ) {
    val isRightOperatorResults =
      { calleeResults: AnalyzeCalleeResults -> binaryExpression.isRightOperator(calleeResults.callable) }

    val allCalleesAreRightOperators = calleesResults.all(isRightOperatorResults)

    val preferredOperatorsResults =
      if (allCalleesAreRightOperators)
        calleesResults
      else
        calleesResults.filter { !isRightOperatorResults(it) }

    if (preferredOperatorsResults.size == 1) {
      registerSingleCalleeProblem(
        visitor,
        binaryExpression,
        preferredOperatorsResults[0],
        context,
        highlightOverride
      )
    }
    else {
      registerWithOverride(
        visitor,
        (if (allCalleesAreRightOperators) binaryExpression.leftExpression else binaryExpression.rightExpression)!!,
        getMultiCalleeProblemMessage(argumentTypes, preferredOperatorsResults, context, isOnTheFly(visitor)),
        highlightOverride
      )
    }
  }

  private fun getMultiCalleeElementToHighlight(callSite: PyCallSiteOwner): PsiElement {
    return when (callSite) {
      is PyCallExpression -> {
        val argumentList = callSite.argumentList

        val result = Optional
          .ofNullable(argumentList)
          .map { it.arguments }
          .filter { it.size == 1 }
          .map<PsiElement> { it[0] }
          .orElse(argumentList)

        result ?: callSite
      }
      is PySubscriptionExpression -> callSite.indexExpression ?: callSite
      else -> callSite
    }
  }

  @InspectionMessage
  private fun getMultiCalleeProblemMessage(
    argumentTypes: List<PyType?>,
    calleesResults: List<AnalyzeCalleeResults>,
    context: TypeEvalContext,
    isOnTheFly: Boolean,
  ): @InspectionMessage String {
    val actualTypesRepresentation = getMultiCalleeActualTypesRepresentation(argumentTypes, context)
    val expectedTypesRepresentation = getMultiCalleePossibleExpectedTypesRepresentation(calleesResults, context, isOnTheFly)

    if (isOnTheFly) {
      return HtmlBuilder()
        .append(PyPsiBundle.message("INSP.type.checker.unexpected.types.prefix")).br().appendRaw(actualTypesRepresentation).br()
        .append(PyPsiBundle.message("INSP.type.checker.expected.types.prefix")).br().appendRaw(expectedTypesRepresentation)
        .wrapWith("html").toString()
    }
    else {
      return PyPsiBundle.message("INSP.type.checker.unexpected.types.prefix") + " " + actualTypesRepresentation + " " +
             PyPsiBundle.message("INSP.type.checker.expected.types.prefix") + " " +
             expectedTypesRepresentation
    }
  }

  private fun isOnTheFly(visitor: PyInspectionVisitor): Boolean {
    val holder = visitor.holder
    return holder != null && holder.isOnTheFly
  }

  private fun getAttributes(type: PyType, context: TypeEvalContext): MutableSet<String?>? {
    if (type is PyStructuralType) {
      return type.attributeNames
    }
    else if (type is PyClassLikeType) {
      return type.getMemberNames(true, context)
    }
    return null
  }

  @NlsSafe
  private fun getMultiCalleeActualTypesRepresentation(
    argumentTypes: List<PyType?>,
    context: TypeEvalContext,
  ): @NlsSafe String = argumentTypes.joinToString(", ", "(", ")") { PythonDocumentationProvider.getTypeName(it, context) }

  @NlsSafe
  private fun getMultiCalleePossibleExpectedTypesRepresentation(
    calleesResults: List<AnalyzeCalleeResults>,
    context: TypeEvalContext,
    isOnTheFly: Boolean,
  ): @NlsSafe String = calleesResults
    .joinToString(if (isOnTheFly) "<br>" else " ") {
      val expectedTypesRepresentation = getMultiCalleeExpectedTypesRepresentation(it.results, context)
      if (isOnTheFly) XmlStringUtil.escapeString(expectedTypesRepresentation) else expectedTypesRepresentation
    }

  private fun getMultiCalleeExpectedTypesRepresentation(
    calleeResults: List<AnalyzeArgumentResult>,
    context: TypeEvalContext,
  ): String = calleeResults
    .joinToString(", ", "(", ")") { result ->
      PythonDocumentationProvider.getTypeName((result.expectedTypeAfterSubstitution ?: result.expectedType), context)
    }
}
