// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.google.common.collect.Sets
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.typing.matchingProtocolDefinitions
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.inspections.PyTypeCheckerInspection.AnalyzeArgumentResult
import com.jetbrains.python.inspections.PyTypeCheckerInspection.AnalyzeCalleeResults
import com.jetbrains.python.inspections.PyTypeCheckerInspectionProblemRegistrar.breakdownTooltip
import com.jetbrains.python.inspections.PyTypeCheckerInspectionProblemRegistrar.breakdownTooltipFromFragment
import com.jetbrains.python.psi.PyAugAssignmentStatement
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyCallSiteOwner
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.impl.PyPsiUtils.getFirstChildOfType
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyStructuralType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeMismatchExplanation
import com.jetbrains.python.psi.types.TypeEvalContext
import java.util.Optional

internal object PyTypeCheckerInspectionProblemRegistrar {
  fun registerProblem(
    holder: ProblemsHolder,
    callSite: PyCallSiteOwner,
    calleesResults: List<AnalyzeCalleeResults>,
    context: TypeEvalContext,
    highlightOverride: ProblemHighlightType?,
  ) {
    val code = suppressionCodeFor(callSite)
    if (calleesResults.size == 1) {
      registerSingleCalleeProblem(
        holder,
        code,
        callSite,
        calleesResults[0],
        context,
        highlightOverride
      )
    }
    else if (!calleesResults.isEmpty()) {
      registerMultiCalleeProblem(holder, code, callSite, calleesResults, context, highlightOverride)
    }
  }

  /**
   * Argument mismatches map to [PyTypeCheckerSuppressionCode.BAD_ARGUMENT_TYPE], except when the call site is
   * an operator (binary / augmented assignment) or a subscription, which get their own dedicated codes.
   */
  private fun suppressionCodeFor(callSite: PyCallSiteOwner): PyTypeCheckerSuppressionCode = when (callSite) {
    is PyBinaryExpression, is PyAugAssignmentStatement -> PyTypeCheckerSuppressionCode.UNSUPPORTED_OPERATOR
    is PySubscriptionExpression -> PyTypeCheckerSuppressionCode.BAD_INDEX
    else -> PyTypeCheckerSuppressionCode.BAD_ARGUMENT_TYPE
  }

  private fun registerSingleCalleeProblem(
    holder: ProblemsHolder,
    code: PyTypeCheckerSuppressionCode,
    callSite: PyCallSiteOwner,
    calleeResults: AnalyzeCalleeResults,
    context: TypeEvalContext,
    highlightOverride: ProblemHighlightType?,
  ) {
    for (argumentResult in calleeResults.results) {
      if (argumentResult.isMatched) continue

      val argument = argumentResult.argument
      val message = getSingleCalleeProblemMessage(argumentResult, context)
      val type = highlightOverride ?: ProblemHighlightType.GENERIC_ERROR_OR_WARNING
      // The breakdown tooltip re-runs the match, so reportWithTooltip invokes the supplier only on-the-fly.
      val expected = argumentResult.expectedTypeAfterSubstitution ?: argumentResult.expectedType
      PyTypeCheckerProblemReporter.reportWithTooltip(holder, code, argument, message, type) {
        breakdownTooltip(message,
                         expected,
                         argumentResult.actualType,
                         context,
                         argument)
      }
    }

    for (unexpectedArgumentForParamSpec in calleeResults.unmatchedArguments) {
      val argument = unexpectedArgumentForParamSpec.argument
      val paramSpecTypeName = unexpectedArgumentForParamSpec.paramSpecType.variableName
      registerWithOverride(
        holder, code, argument,
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
                holder, code, rpar, PyPsiBundle.problemMessage(
                  "INSP.type.checker.unfilled.parameter.for.paramspec", parameterName,
                  paramSpecTypeName
                ), highlightOverride
              )
            }
          }

          for (unfilledParameterFromParamSpec in calleeResults.unfilledPositionalVarargs) {
            val varargName = unfilledParameterFromParamSpec.varargName
            val expectedType = PyInspectionMessages.CodifiedParam.ofType(unfilledParameterFromParamSpec.expectedType, rpar, context)
            registerWithOverride(
              holder, code, rpar, PyPsiBundle.problemMessage("INSP.type.checker.unfilled.vararg", varargName, expectedType),
              highlightOverride
            )
          }
        }
      }
    }
  }

  private fun registerWithOverride(
    holder: ProblemsHolder,
    code: PyTypeCheckerSuppressionCode,
    element: PsiElement,
    message: PyInspectionMessages.ProblemMessage,
    highlightOverride: ProblemHighlightType?,
  ) {
    val type = highlightOverride ?: ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    PyTypeCheckerProblemReporter.report(holder, code, element, message, type)
  }

  private fun registerMultiCalleeProblem(
    holder: ProblemsHolder,
    code: PyTypeCheckerSuppressionCode,
    callSite: PyCallSiteOwner,
    calleesResults: List<AnalyzeCalleeResults>,
    context: TypeEvalContext,
    highlightOverride: ProblemHighlightType?,
  ) {
    if (callSite is PyBinaryExpression) {
      registerMultiCalleeProblemForBinaryExpression(holder, code, callSite, calleesResults, context, highlightOverride)
    }
    else {
      registerMultiCalleeProblem(holder, code, getMultiCalleeElementToHighlight(callSite), calleesResults, context, highlightOverride)
    }
  }

  private fun registerMultiCalleeProblem(
    holder: ProblemsHolder,
    code: PyTypeCheckerSuppressionCode,
    element: PsiElement?,
    calleesResults: List<AnalyzeCalleeResults>,
    context: TypeEvalContext,
    highlightOverride: ProblemHighlightType?,
  ) {
    val header = PyMismatchTooltips.header(calleesResults.map { it.callable })
    val argumentSlots = getReferenceResults(calleesResults).map { argumentResult ->
      PyMismatchTooltips.argumentSlot(argumentResult.argument, argumentResult.actualType, context,
                                      !argumentMatchesNoCallee(argumentResult.argument, calleesResults))
    }
    val expectedRows = calleesResults.map { calleeResults ->
      calleeResults.results.map { getExpectedParameterSlot(it, context, it.isMatched) }
    }

    val description = PyMismatchTooltips.description(header, argumentSlots, expectedRows)
    val highlightType = highlightOverride ?: ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    // The aligned-table tooltip is only worth building on-the-fly; reportWithTooltip invokes the supplier then.
    PyTypeCheckerProblemReporter.reportWithTooltip(holder, code, element, description, highlightType) {
      PyMismatchTooltips.tooltip(header, argumentSlots, expectedRows)
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

    val anchor = argumentResult.argument
    val actualTypeParam = PyInspectionMessages.CodifiedParam.ofType(actualType, anchor, context)

    if (expectedType is PyStructuralType) {
      val expectedAttributes = expectedType.attributeNames
      val actualAttributes = getAttributes(actualType, context)

      if (actualAttributes != null) {
        val missingAttributes = Sets.difference<String?>(expectedAttributes, actualAttributes)
        return PyPsiBundle.problemMessage(
          "INSP.type.checker.type.does.not.have.expected.attribute",
          actualTypeParam, missingAttributes.size,
          PyInspectionMessages.CodifiedParam.joinNames(missingAttributes.filterNotNull())
        )
      }
    }

    val expectedTypeAfterSubstitution = argumentResult.expectedTypeAfterSubstitution
    val expectedTypeParam = PyInspectionMessages.CodifiedParam.ofType(expectedType, anchor, context, true)
    val expectedSubstitutedParam = if (expectedTypeAfterSubstitution != null && expectedTypeAfterSubstitution != expectedType)
      PyInspectionMessages.CodifiedParam.ofType(expectedTypeAfterSubstitution, anchor, context)
    else
      null

    if (matchingProtocolDefinitions(expectedType, actualType, context)) {
      if (expectedSubstitutedParam != null) {
        return PyPsiBundle.problemMessage(
          "INSP.type.checker.only.concrete.class.can.be.used.where.matched.protocol.expected",
          expectedSubstitutedParam, expectedTypeParam
        )
      }
      else {
        return PyPsiBundle.problemMessage("INSP.type.checker.only.concrete.class.can.be.used.where.protocol.expected", expectedTypeParam)
      }
    }

    if (expectedSubstitutedParam != null) {
      return enrichWithCallableDiff(
        PyPsiBundle.problemMessage(
          "INSP.type.checker.expected.matched.type.got.type.instead", expectedSubstitutedParam, expectedTypeParam,
          actualTypeParam
        ),
        expectedTypeAfterSubstitution, actualType, context
      )
    }
    else {
      return enrichWithCallableDiff(
        PyPsiBundle.problemMessage("INSP.type.checker.expected.type.got.type.instead", expectedTypeParam, actualTypeParam),
        expectedType, actualType, context
      )
    }
  }

  /**
   * Replaces the tooltip of [base] with an aligned callable type diff (see [PyTypeDiff]) when both
   * [expected] and [actual] are callables; otherwise returns [base] unchanged. The plain-text description is
   * always preserved for the Problems view.
   */
  private fun enrichWithCallableDiff(
    base: PyInspectionMessages.ProblemMessage,
    expected: PyType?,
    actual: PyType?,
    context: TypeEvalContext,
  ): PyInspectionMessages.ProblemMessage {
    val diff = PyTypeDiff.diffTooltip(expected, actual, context)
    return if (diff != null) base.copy(tooltip = diff) else base
  }

  private fun registerMultiCalleeProblemForBinaryExpression(
    holder: ProblemsHolder,
    code: PyTypeCheckerSuppressionCode,
    binaryExpression: PyBinaryExpression,
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
        holder,
        code,
        binaryExpression,
        preferredOperatorsResults[0],
        context,
        highlightOverride
      )
    }
    else {
      registerMultiCalleeProblem(
        holder,
        code,
        if (allCalleesAreRightOperators) binaryExpression.leftExpression else binaryExpression.rightExpression,
        preferredOperatorsResults, context, highlightOverride
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

  /**
   * Results of the callee that maps the most arguments; used as the source of actual argument types,
   * so that their order is consistent with the per-callee expected parameter rows.
   */
  private fun getReferenceResults(calleesResults: List<AnalyzeCalleeResults>): List<AnalyzeArgumentResult> =
    calleesResults.map { it.results }.maxByOrNull { it.size } ?: emptyList()

  private fun argumentMatchesNoCallee(
    argument: PyExpression,
    calleesResults: List<AnalyzeCalleeResults>,
  ): Boolean = calleesResults.none { calleeResults ->
    calleeResults.results.any { it.argument === argument && it.isMatched }
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

  private fun getExpectedParameterSlot(
    argumentResult: AnalyzeArgumentResult,
    context: TypeEvalContext,
    matched: Boolean,
  ): PyMismatchTooltips.Slot {
    val type = argumentResult.expectedTypeAfterSubstitution ?: argumentResult.expectedType
    val typeName = PythonDocumentationProvider.getTypeName(type, context)
    val parameter = argumentResult.parameter ?: return PyMismatchTooltips.Slot("", typeName, matched)
    return PyMismatchTooltips.parameterSlot(parameter, typeName, matched)
  }

  /**
   * Renders [headlineFragment] (already an HTML fragment, with any `<code>` spans) followed by the
   * [explanation] tree as an HTML tooltip (on-the-fly only). Each level is indented; a node's message marks
   * code-like spans with backticks, which become `<code>` blocks while the surrounding text is escaped. The
   * result is used as the problem's tooltip, not its description, so batch results stay one line.
   */
  @NlsContexts.Tooltip
  private fun breakdownTooltipFromFragment(
    @NlsContexts.Tooltip headlineFragment: String,
    explanation: PyTypeMismatchExplanation,
  ): @NlsContexts.Tooltip String {
    val builder = HtmlBuilder().appendRaw(headlineFragment)
    appendBreakdownNodes(builder, listOf(explanation), 1)
    return builder.wrapWith("html").toString()
  }

  /** [breakdownTooltipFromFragment] with an enriched headline; its `<code>` spans (and any links) are kept. */
  @NlsContexts.Tooltip
  @JvmStatic
  fun breakdownTooltip(
    headline: PyInspectionMessages.ProblemMessage,
    explanation: PyTypeMismatchExplanation,
  ): @NlsContexts.Tooltip String =
    breakdownTooltipFromFragment(PyInspectionMessages.tooltipFragment(headline), explanation)

  /**
   * The breakdown tooltip explaining why [actual] doesn't match [expected], or null when the failure category
   * isn't instrumented (no [PyTypeChecker.explainMismatch] result). Pass as the on-the-fly tooltip supplier to
   * [PyInspectionVisitor.registerProblem]; it re-runs the match, so it must be invoked only on-the-fly.
   *
   * [anchor] is the element the problem is reported on; it is used to resolve type and class names in the
   * breakdown to their declarations so they render as clickable links (as in the enriched headline).
   */
  @NlsContexts.Tooltip
  @JvmStatic
  fun breakdownTooltip(
    headline: PyInspectionMessages.ProblemMessage,
    expected: PyType?,
    actual: PyType?,
    context: TypeEvalContext,
    anchor: PsiElement?,
  ): @NlsContexts.Tooltip String? =
    PyTypeChecker.explainMismatch(expected, actual, context, anchor)?.let { breakdownTooltip(headline, it) }

  private fun appendBreakdownNodes(
    builder: HtmlBuilder,
    nodes: List<PyTypeMismatchExplanation>,
    depth: Int,
  ) {
    for (node in nodes) {
      builder.br().appendRaw(StringUtil.repeat("&nbsp;", depth * 2))
        .appendRaw(PyInspectionMessages.tooltipFragment(node.message))
      appendBreakdownNodes(builder, node.children, depth + 1)
    }
  }
}
