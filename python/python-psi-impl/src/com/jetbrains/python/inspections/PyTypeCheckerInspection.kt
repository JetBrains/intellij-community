// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.ast.PyAstFunction
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil.getScopeOwner
import com.jetbrains.python.codeInsight.stdlib.PyStdlibTypeProvider
import com.jetbrains.python.codeInsight.stdlib.PyStdlibTypeProvider.getEnumAttributeInfo
import com.jetbrains.python.codeInsight.stdlib.PyStdlibTypeProvider.getEnumValueType
import com.jetbrains.python.codeInsight.stdlib.PyStdlibTypeProvider.isCustomEnum
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.Companion.coroutineOrGeneratorElementType
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.Companion.isInsideTypeHint
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.GeneratorTypeDescriptor
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.GeneratorTypeDescriptor.Companion.fromGeneratorOrProtocol
import com.jetbrains.python.codeInsight.typing.isProtocol
import com.jetbrains.python.codeInsight.typing.matchingProtocolDefinitions
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.inspections.quickfix.PyMakeFunctionReturnTypeQuickFix
import com.jetbrains.python.psi.PyAnnotationOwner
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyAugAssignmentStatement
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyCallExpression.PyArgumentsMapping
import com.jetbrains.python.psi.PyCallSiteOwner
import com.jetbrains.python.psi.PyCallable
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyComprehensionElement
import com.jetbrains.python.psi.PyDictLiteralExpression
import com.jetbrains.python.psi.PyDoubleStarExpression
import com.jetbrains.python.psi.PyEllipsisLiteralExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyForStatement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyListLiteralExpression
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyParameterList
import com.jetbrains.python.psi.PyParenthesizedExpression
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyReferenceOwner
import com.jetbrains.python.psi.PyReturnStatement
import com.jetbrains.python.psi.PySequenceExpression
import com.jetbrains.python.psi.PyStarArgument
import com.jetbrains.python.psi.PyStarExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.PyTypeCommentOwner
import com.jetbrains.python.psi.PyUtil.isAttribute
import com.jetbrains.python.psi.PyUtil.isClassAttribute
import com.jetbrains.python.psi.PyUtil.isEmptyFunction
import com.jetbrains.python.psi.PyUtil.isInitMethod
import com.jetbrains.python.psi.PyUtil.peelArgument
import com.jetbrains.python.psi.PyWithStatement
import com.jetbrains.python.psi.PyYieldExpression
import com.jetbrains.python.psi.impl.PyBuiltinCache.Companion.getInstance
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.impl.PyCallExpressionHelper.analyzeArguments
import com.jetbrains.python.psi.impl.PyCallExpressionHelper.mapArguments
import com.jetbrains.python.psi.impl.PyPsiUtils.flattenParens
import com.jetbrains.python.psi.impl.PySubscriptionExpressionImpl
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyABCUtil.isSubtype
import com.jetbrains.python.psi.types.PyAnyType.Companion.unknown
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.PyCallableParameterListType
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyCollectionType
import com.jetbrains.python.psi.types.PyCollectionTypeImpl
import com.jetbrains.python.psi.types.PyConcatenateType
import com.jetbrains.python.psi.types.PyDescriptorTypeUtil.getExpectedValueTypeForDunderSet
import com.jetbrains.python.psi.types.PyInstantiableType
import com.jetbrains.python.psi.types.PyLiteralType.Companion.promoteToLiteral
import com.jetbrains.python.psi.types.PyLiteralType.Companion.upcastLiteralToClass
import com.jetbrains.python.psi.types.PyNeverType
import com.jetbrains.python.psi.types.PyParamSpecType
import com.jetbrains.python.psi.types.PyPositionalVariadicType
import com.jetbrains.python.psi.types.PySelfType
import com.jetbrains.python.psi.types.PySentinelType
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker.GenericSubstitutions
import com.jetbrains.python.psi.types.PyTypeChecker.getTargetTypeFromTupleAssignment
import com.jetbrains.python.psi.types.PyTypeChecker.hasGenerics
import com.jetbrains.python.psi.types.PyTypeChecker.isUnknown
import com.jetbrains.python.psi.types.PyTypeChecker.match
import com.jetbrains.python.psi.types.PyTypeChecker.substitute
import com.jetbrains.python.psi.types.PyTypeChecker.unifyReceiver
import com.jetbrains.python.psi.types.PyTypeInferenceCspFactory.unifyReceiver
import com.jetbrains.python.psi.types.PyTypeParameterMapping
import com.jetbrains.python.psi.types.PyTypeParameterType
import com.jetbrains.python.psi.types.PyTypeUtil.derefOrUnknown
import com.jetbrains.python.psi.types.PyTypedDictType
import com.jetbrains.python.psi.types.PyTypedDictType.Companion.checkExpression
import com.jetbrains.python.psi.types.PyTypedDictType.Companion.isDictExpression
import com.jetbrains.python.psi.types.PyTypedDictType.ExtraKeyError
import com.jetbrains.python.psi.types.PyTypedDictType.MissingKeysError
import com.jetbrains.python.psi.types.PyTypedDictType.TypeCheckingResult
import com.jetbrains.python.psi.types.PyTypedDictType.ValueTypeError
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.PyUnpackedTupleType
import com.jetbrains.python.psi.types.PyUnpackedTupleTypeImpl
import com.jetbrains.python.psi.types.PyUnpackedTypedDictType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.isNoneType
import com.jetbrains.python.psi.types.isObject
import com.jetbrains.python.pyi.PyiUtil.isOverload
import org.jetbrains.annotations.PropertyKey
import java.util.Objects
import kotlin.math.min

open class PyTypeCheckerInspection : PyInspection() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor {
    if (LOG.isDebugEnabled) {
      session.putUserData(TIME_KEY, System.nanoTime())
    }
    val context = PyInspectionVisitor.getContext(session)
    val visitor = Visitor(holder, context)
    return PyReachableElementVisitor(visitor, context)
  }

  open class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {
    protected override fun getHolder(): ProblemsHolder {
      val holder = checkNotNull(super.getHolder())
      return holder
    }

    // TODO: Visit decorators with arguments
    override fun visitPyCallExpression(node: PyCallExpression) {
      checkCallSite(node)
    }

    override fun visitPyBinaryExpression(node: PyBinaryExpression) {
      checkCallSite(node)
    }

    override fun visitPyAugAssignmentStatement(node: PyAugAssignmentStatement) {
      checkCallSite(node)
      visitPyTargetExpression(node.assignmentTarget)
    }

    override fun visitPySubscriptionExpression(node: PySubscriptionExpression) {
      val operandType = myTypeEvalContext.getType(node.operand)
      if (operandType is PyTupleType && !operandType.isHomogeneous) {
        val indexExpression = node.indexExpression
        for (index in PySubscriptionExpressionImpl.getIndexExpressionPossibleValues(
          indexExpression,
          myTypeEvalContext,
          Int::class.java
        )) {
          val count = operandType.elementCount
          if (index < -count || index >= count) {
            registerProblem(indexExpression, PyPsiBundle.message("INSP.type.checker.tuple.index.out.of.range"))
          }
        }
      }
      // Type check in TypedDict subscription expressions cannot be properly done because each key should have its own value type,
      // so this case is covered by PyTypedDictInspection
      if (operandType is PyTypedDictType) return
      // Don't type check __class_getitem__ calls inside type hints. Normally these are not type hinted as a construct
      // special-cased by type checkers
      if (isInsideTypeHint(node, myTypeEvalContext)) return
      checkCallSite(node)
    }

    override fun visitPyForStatement(node: PyForStatement) {
      checkIteratedValue(node.forPart.source, node.isAsync)
    }

    override fun visitPyWithStatement(node: PyWithStatement) {
      for (withItem in node.withItems) {
        checkContextManagerValue(withItem.expression, node.isAsync)
      }
    }

    override fun visitPyReturnStatement(node: PyReturnStatement) {
      val owner = getScopeOwner(node)
      if (owner is PyFunction) {
        if (hasExplicitType(owner)) {
          val expected: PyType? = getExpectedReturnStatementType(owner, myTypeEvalContext)
          if (expected == null) return

          // We cannot just match annotated and inferred types, as we cannot promote inferred to Literal
          val returnExpr = node.expression
          if (expected is PyTypedDictType) {
            if (returnExpr != null && isDictExpression(returnExpr, myTypeEvalContext)) {
              reportTypedDictProblems(expected, returnExpr)
              return
            }
          }

          val actual = if (returnExpr != null) tryPromotingType(returnExpr, expected) else getInstance(node).noneType
          if (!matchesExpectedType(expected, actual, returnExpr, null)) {
            registerProblem(returnExpr ?: node, typeMismatchMessage(expected, actual),
                            effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING),
                            PyMakeFunctionReturnTypeQuickFix(owner, myTypeEvalContext))
          }
        }
      }
    }

    override fun visitPyYieldExpression(node: PyYieldExpression) {
      val owner = getScopeOwner(node)
      if (owner !is PyFunction) return

      if (node.isDelegating) {
        visitDelegatingYieldExpression(node, owner)
        return
      }

      val annotatedGeneratorDesc = getGeneratorDescriptorFromAnnotation(owner, node)
      if (annotatedGeneratorDesc == null) return

      checkYieldType(annotatedGeneratorDesc.yieldType, node, owner)
    }

    private fun visitDelegatingYieldExpression(node: PyYieldExpression, function: PyFunction) {
      assert(node.isDelegating)

      val yieldExpr = node.expression
      if (yieldExpr == null) return

      val delegateType = myTypeEvalContext.getType(yieldExpr)
      if (delegateType == null) return

      val delegateDesc = fromGeneratorOrProtocol(delegateType, myTypeEvalContext)
      if (delegateDesc != null && delegateDesc.isAsync) {
        val delegateName = PythonDocumentationProvider.getTypeName(delegateType, myTypeEvalContext)
        registerProblem(yieldExpr, PyPsiBundle.problemMessage("INSP.type.checker.yield.from.async.generator", delegateName))
        return
      }

      if (checkIteratedValue(yieldExpr, false)) return

      val annotatedGeneratorDesc = getGeneratorDescriptorFromAnnotation(function, node)
      if (annotatedGeneratorDesc == null) return

      if (checkYieldType(annotatedGeneratorDesc.yieldType, node, function)) return

      // Reversed because SendType is contravariant
      val expectedSendType = annotatedGeneratorDesc.sendType
      if (delegateDesc != null && !match(delegateDesc.sendType, expectedSendType, myTypeEvalContext)) {
        val expectedName = PythonDocumentationProvider.getVerboseTypeName(expectedSendType, myTypeEvalContext)
        val actualName = PythonDocumentationProvider.getTypeName(delegateDesc.sendType, myTypeEvalContext)
        registerProblem(yieldExpr, PyPsiBundle.problemMessage("INSP.type.checker.yield.from.send.type.mismatch", expectedName, actualName))
      }
    }

    private fun getGeneratorDescriptorFromAnnotation(
      function: PyFunction,
      yieldExpr: PyYieldExpression,
    ): GeneratorTypeDescriptor? {
      if (!hasExplicitType(function)) return null

      val annotatedReturnType = myTypeEvalContext.getReturnType(function)
      if (annotatedReturnType == null) return null

      val annotatedGeneratorDesc = fromGeneratorOrProtocol(annotatedReturnType, myTypeEvalContext)
      if (annotatedGeneratorDesc == null) {
        val inferredReturnType = function.getInferredReturnType(myTypeEvalContext)
        if (!match(annotatedReturnType, inferredReturnType, myTypeEvalContext)) {
          registerProblem(yieldExpr, typeMismatchMessage(annotatedReturnType, inferredReturnType),
                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                          PyMakeFunctionReturnTypeQuickFix(function, myTypeEvalContext))
        }
        return null
      }
      return annotatedGeneratorDesc
    }

    private fun checkYieldType(expectedYieldType: PyType?, node: PyYieldExpression, function: PyFunction): Boolean {
      val thisYieldType = node.getYieldType(myTypeEvalContext)
      if (!matchesExpectedType(expectedYieldType, thisYieldType, node.expression, null)) {
        val yieldExpr = node.expression
        val expectedName = PythonDocumentationProvider.getVerboseTypeName(expectedYieldType, myTypeEvalContext)
        val actualName = PythonDocumentationProvider.getTypeName(thisYieldType, myTypeEvalContext)
        registerProblem(
          yieldExpr ?: node,
          PyPsiBundle.problemMessage("INSP.type.checker.yield.type.mismatch", expectedName, actualName),
          effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING),
          PyMakeFunctionReturnTypeQuickFix(function, myTypeEvalContext)
        )
        return true
      }
      return false
    }

    override fun visitPyReferenceExpression(node: PyReferenceExpression) {
      checkClassAttributeAccess(node)
    }

    override fun visitPyAssignmentStatement(node: PyAssignmentStatement) {
      val lhs = flattenParens(node.leftHandSideExpression)
      val rhs = node.assignedValue
      if ((lhs !is PyTupleExpression && lhs !is PyListLiteralExpression) || rhs == null) return
      val lhsSeq: PySequenceExpression = lhs

      // Check that the RHS is iterable
      if (checkUnpackIterableValue(rhs)) return

      val rhsType = myTypeEvalContext.getType(rhs)
      if (rhsType !is PyTupleType || rhsType.isHomogeneous) return

      val targets = lhsSeq.elements
      val lhsStarCount = targets.filterIsInstance<PyStarExpression>().size

      // The RHS value count. A starred RHS element contributes its operand's length only when that operand is a
      // statically known (heterogeneous) tuple; an unbounded operand (e.g. `*list_value`) makes the count
      // indeterminate, in which case the balance check is skipped.
      val rhsCount = if (rhs is PyTupleExpression && rhs.elements.any { it is PyStarExpression }) {
        getUnpackedTupleLength(rhs)
      }
      else {
        rhsType.elementCount
      }
      if (rhsCount >= 0) {
        if (lhsStarCount > 1) {
          registerProblem(lhs, PyPsiBundle.message("INSP.tuple.assignment.balance.only.one.starred.expression.allowed.in.assignment"))
          return
        }
        if (lhsStarCount == 0 && targets.size != rhsCount) {
          val key = if (targets.size < rhsCount) {
            "INSP.tuple.assignment.balance.too.many.values.to.unpack"
          }
          else {
            "INSP.tuple.assignment.balance.need.more.values.to.unpack"
          }
          registerProblem(rhs, PyPsiBundle.message(key, targets.size, rhsCount),
                          effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
          return
        }
        if (lhsStarCount == 1 && targets.size - 1 > rhsCount) {
          registerProblem(rhs, PyPsiBundle.message("INSP.tuple.assignment.balance.need.more.values.to.unpack",
                                                   targets.size - 1, rhsCount),
                          effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
          return
        }
      }

      // Per-element type mismatch check for annotated targets with a non-tuple RHS (`x, y = expr` / `[x] = expr`).
      // findAssignedValue() yields a synthetic subscription for these, so the mismatch is reported on the RHS itself.
      // A tuple RHS (`x, y = 1, 2` / `[x] = 1, 2`) maps to real value elements and is handled by
      // visitPyTargetExpression via findAssignedValue().
      if (lhsStarCount == 0 && rhs !is PyTupleExpression) {
        for (target in targets) {
          if (target !is PyTargetExpression) continue
          if (!targetOrResolvedHasExplicitType(target)) continue
          val annotatedType = myTypeEvalContext.getType(target)
          val unpackedType = getTargetTypeFromTupleAssignment(target, lhsSeq, rhsType) ?: continue
          if (match(annotatedType, unpackedType, myTypeEvalContext)) continue
          val displayType = upcastLiteralToClass(unpackedType)
          registerProblem(rhs,
                          PyPsiBundle.problemMessage("INSP.type.checker.expected.type.got.type.instead",
                                                     PythonDocumentationProvider.getVerboseTypeName(annotatedType, myTypeEvalContext),
                                                     PythonDocumentationProvider.getTypeName(displayType, myTypeEvalContext)),
                          effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
          // stop after the first error, because otherwise we might start reporting different type errors on the same element
          return
        }
      }
    }

    override fun visitPyStarExpression(node: PyStarExpression) {
      val parent = node.parent
      if (parent is PySequenceExpression) {
        // Skip star expressions that are assignment targets: `a, *b = ...`
        val possibleLhs = PsiTreeUtil.skipParentsOfType(parent, PyParenthesizedExpression::class.java)
        if (possibleLhs is PyAssignmentStatement && flattenParens(possibleLhs.leftHandSideExpression) === parent) {
          // Check type annotation compatibility for annotated star targets like `x: int; (*x,) = [1, 2, 3]`
          val innerExpr = node.expression
          if (innerExpr is PyTargetExpression && targetOrResolvedHasExplicitType(innerExpr)) {
            val rhs = possibleLhs.assignedValue
            if (rhs != null) {
              val rhsType = myTypeEvalContext.getType(rhs)
              if (rhsType is PyCollectionType) {
                val elementType = upcastLiteralToClass(rhsType.iteratedItemType)
                val listClass = getInstance(node).getClass("list")
                if (listClass != null) {
                  val actualType = PyCollectionTypeImpl(listClass, false, listOf(elementType))
                  val annotatedType = myTypeEvalContext.getType(innerExpr)
                  if (annotatedType != null && !isUnknown(annotatedType, myTypeEvalContext) &&
                      !match(annotatedType, actualType, myTypeEvalContext)) {
                    registerProblem(rhs,
                                    PyPsiBundle.problemMessage("INSP.type.checker.expected.type.got.type.instead",
                                                               PythonDocumentationProvider.getVerboseTypeName(annotatedType, myTypeEvalContext),
                                                               PythonDocumentationProvider.getTypeName(actualType, myTypeEvalContext)),
                                    effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
                  }
                }
              }
            }
          }
          return
        }
        // Check iterability of starred operand in sequence literals `[*a], {*a}, (*a,)`
        // Skip type unpack expressions in type hints: [*tuple[T]], (*Ts,), etc.
        // TODO: consider Annotated[T, [*1]]
        if (isInsideTypeHint(node, myTypeEvalContext)) return
        checkUnpackIterableValue(node.expression)
      }
    }

    override fun visitPyDoubleStarExpression(node: PyDoubleStarExpression) {
      // Check that **expr in dict literals ({**a}) has a mapping type
      if (node.parent is PyDictLiteralExpression) {
        checkUnpackMappingValue(node.expression)
      }
    }

    override fun visitPyStarArgument(node: PyStarArgument) {
      if (node.isKeyword) {
        checkUnpackMappingValue(node.expression)
      }
      else {
        checkUnpackIterableValue(node.expression)
      }
    }

    override fun visitPyTargetExpression(node: PyTargetExpression) {
      checkClassAttributeAccess(node)
      val assignedValue = node.findAssignedValue()
      if (assignedValue == null) return

      val scopeOwner = getScopeOwner(node)
      if (scopeOwner is PyClass && isCustomEnum(scopeOwner, myTypeEvalContext)) {
        val info = getEnumAttributeInfo(scopeOwner, node, myTypeEvalContext)
        if (info == null || info.attributeKind != PyStdlibTypeProvider.EnumAttributeKind.MEMBER) return

        val expected = getEnumValueType(scopeOwner, myTypeEvalContext)
        val actual = info.assignedValueType
        if (!match(expected, actual, myTypeEvalContext)) {
          registerProblem(assignedValue, typeMismatchMessage(expected, actual))
        }
        return
      }

      var expected = myTypeEvalContext.getType(node)

      if (scopeOwner is PyClass) {
        if (!targetOrResolvedHasExplicitType(node)) return
      }

      if (node.isQualified) {
        val substitutions = unifyReceiver(node.qualifier, myTypeEvalContext)
        expected = substitute(expected, substitutions, myTypeEvalContext)
      }

      var isDescriptor = false

      val classAttrType = getClassAttributeType(node)
      if (classAttrType != null) {
        val dunderSetValueType =
          getExpectedValueTypeForDunderSet(node, classAttrType.get(), myTypeEvalContext)
        if (dunderSetValueType != null) {
          expected = dunderSetValueType.get()
          isDescriptor = true
        }
      }

      if (expected is PyTypedDictType && isDictExpression(assignedValue, myTypeEvalContext)) {
        reportTypedDictProblems(expected, assignedValue)
        return
      }

      val actual = tryPromotingType(assignedValue, expected)

      if (expected is PySentinelType) {
        if (actual.isObject) return
      }

      if (!matchesExpectedType(expected, actual, assignedValue, null)) {
        val isAugAssignment = node.parent is PyAugAssignmentStatement
        val message =
          if (isDescriptor)
            typeMismatchMessage(
              expected,
              actual,
              "INSP.type.checker.expected.type.from.dunder.set.got.type.instead"
            )
          else
            if (isAugAssignment)
              typeMismatchMessage(
                expected,
                actual,
                "INSP.type.checker.expected.type.from.aug.assignment.got.type.instead"
              )
            else
              typeMismatchMessage(expected, actual)
        registerProblem(
          assignedValue,
          message,
          effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        )
      }
    }

    private fun typeMismatchMessage(
      expected: PyType?,
      actual: PyType?,
    ): PyInspectionMessages.ProblemMessage {
      return typeMismatchMessage(expected, actual, "INSP.type.checker.expected.type.got.type.instead")
    }

    private fun typeMismatchMessage(
      expected: PyType?,
      actual: PyType?,
      @PropertyKey(resourceBundle = PyPsiBundle.BUNDLE) messageKey: @PropertyKey(resourceBundle = PyPsiBundle.BUNDLE) String,
    ): PyInspectionMessages.ProblemMessage {
      val expectedName = PythonDocumentationProvider.getVerboseTypeName(expected, myTypeEvalContext)
      val actualName = PythonDocumentationProvider.getTypeName(actual, myTypeEvalContext)
      return PyPsiBundle.problemMessage(messageKey, expectedName, actualName)
    }

    private fun matchesExpectedType(
      expected: PyType?,
      actual: PyType?,
      expression: PyExpression?,
      substitutions: GenericSubstitutions?,
    ): Boolean {
      val matches = if (substitutions == null)
        match(expected, actual, myTypeEvalContext)
      else
        match(expected, actual, myTypeEvalContext, substitutions)
      if (matches) return true
      return isCovariantMatchTempFix(expected, actual, expression, substitutions)
    }

    /**
     * The failing subtype check could be due to respecting variance.
     * However, the underlying reason is that the `actual` type was not correctly inferred.
     * As a temporary solution, we mimic a covariant subtype check. (TODO PY-89564)
     */
    private fun isCovariantMatchTempFix(
      expected: PyType?, actual: PyType?, expExpr: PyExpression?,
      substitutions: GenericSubstitutions?,
    ): Boolean {
      val expectedSubst = if (substitutions == null) expected else substitute(expected, substitutions, myTypeEvalContext)
      val actualSubst = if (substitutions == null) actual else substitute(actual, substitutions, myTypeEvalContext)
      if (expectedSubst is PyCollectionType && actualSubst is PyCollectionType) {
        val expClassType = expectedSubst.pyClass.getType(myTypeEvalContext)
        val actClassType = actualSubst.pyClass.getType(myTypeEvalContext)
        val isCreational = expExpr is PySequenceExpression
                           || expExpr is PyCallExpression && expExpr.callee !is PySubscriptionExpression || expExpr is PyParenthesizedExpression && expExpr.containedExpression is PyTupleExpression
        val paramMapping = PyTypeParameterMapping.mapByShape(
          expectedSubst.elementTypes,
          actualSubst.elementTypes,
          PyTypeParameterMapping.Option.USE_DEFAULTS
        )
        if (isCreational
            && paramMapping != null && match(expClassType, actClassType, myTypeEvalContext)
        ) {
          var allElementsMatch = true
          for (i in paramMapping.mappedTypes.indices) {
            val couple = paramMapping.mappedTypes[i]
            val expET = couple.first
            var actET = couple.second
            if (actET is PyUnpackedTupleType && actET.isUnbound) {
              actET = actET.elementTypes.first()
            }
            if (!match(expET, actET, myTypeEvalContext) && !isCovariantMatchTempFix(expET, actET, expExpr, substitutions)) {
              allElementsMatch = false
              break
            }
          }
          if (allElementsMatch) {
            return true
          }
        }
      }
      return false
    }

    // Using generic classes (parameterized or not) to access attributes will result in type check failure.
    private fun <T> checkClassAttributeAccess(expression: T) where T : PyQualifiedExpression?, T : PyReferenceOwner? {
      val qualifier = expression!!.qualifier
      if (qualifier != null) {
        val qualifierType = myTypeEvalContext.getType(qualifier)
        if (qualifierType is PyClassType && qualifierType.isDefinition) {
          val resolved = expression.getReference(PyResolveContext.defaultContext(myTypeEvalContext)).resolve()
          if (resolved is PyTargetExpression && isClassAttribute(resolved)) {
            val targetType = myTypeEvalContext.getType(resolved)
            if (requiresTypeSpecialization(targetType)) {
              val nameElement = expression.nameElement
              registerProblem(
                nameElement?.psi,
                PyPsiBundle.message("INSP.type.checker.access.to.generic.instance.variables.via.class.is.ambiguous")
              )
            }
          }
        }
      }
    }

    private fun <T> getClassAttributeType(attribute: T): Ref<PyType?>? where T : PyQualifiedExpression?, T : PyReferenceOwner? {
      if (!attribute!!.isQualified) return null
      val definition = attribute.getReference(PyResolveContext.defaultContext(myTypeEvalContext)).resolve()
      if (!(definition is PyTargetExpression && isAttribute(definition))) return null
      return Ref.create<PyType?>(myTypeEvalContext.getType(definition))
    }

    private fun reportTypedDictProblems(expectedType: PyTypedDictType, expression: PyExpression) {
      val result = TypeCheckingResult()
      checkExpression(expectedType, expression, myTypeEvalContext, result)
      result.valueTypeErrors.forEach { error: ValueTypeError? ->
        registerProblem(
          error!!.actualExpression,
          typeMismatchMessage(error.expectedType, error.actualType),
          effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        )
      }
      result.extraKeys.forEach { error: ExtraKeyError? ->
        registerProblem(
          Objects.requireNonNullElse<PyExpression?>(error!!.actualExpression, expression),
          PyPsiBundle.problemMessage("INSP.type.checker.typed.dict.extra.key", error.key, error.expectedTypedDictName)
        )
      }
      result.missingKeys.forEach { error: MissingKeysError? ->
        registerProblem(
          if (error!!.actualExpression != null) error.actualExpression else expression,
          PyPsiBundle.problemMessage(
            "INSP.type.checker.typed.dict.missing.keys", error.expectedTypedDictName,
            error.missingKeys.size,
            PyInspectionMessages.CodifiedParam.joinNames(error.missingKeys)
          )
        )
      }
    }

    private fun reportUnpackedTypedDictProblems(
      expectedType: PyUnpackedTypedDictType,
      expression: PyExpression,
    ) {
      var expression: PyExpression? = expression
      if (expression is PyStarArgument) {
        expression = PsiTreeUtil.findChildOfType(expression, PyExpression::class.java)
      }
      if (expression == null) return
      val argumentType = myTypeEvalContext.getType(expression)
      val typedDictType = expectedType.typedDictType
      if (isDictExpression(expression, myTypeEvalContext)) {
        reportTypedDictProblems(typedDictType, expression)
        return
      }
      if (!match(typedDictType, argumentType, myTypeEvalContext)) {
        registerProblem(
          expression,
          PyPsiBundle.problemMessage(
            "INSP.type.checker.expected.type.got.type.instead",
            PythonDocumentationProvider.getTypeName(typedDictType, myTypeEvalContext),
            PythonDocumentationProvider.getTypeName(argumentType, myTypeEvalContext)
          )
        )
      }
    }

    private fun tryPromotingType(expr: PyExpression, expected: PyType?): PyType? {
      val promotedToLiteral = promoteToLiteral(expr, expected, myTypeEvalContext, null)
      if (promotedToLiteral != null) return promotedToLiteral
      return myTypeEvalContext.getType(expr)
    }

    override fun visitPyFunction(node: PyFunction) {
      if (hasExplicitType(node)) {
        val annotation = node.annotation
        val expected: PyType? = getExpectedReturnStatementType(node, myTypeEvalContext)
        val noneType: PyType? = getInstance(node).noneType
        val returnsNone = expected.isNoneType
        val returnsOptional = match(expected, noneType, myTypeEvalContext)

        if (expected != null && !returnsOptional && !isEmptyFunction(node)) {
          val returnPoints = node.getReturnPoints(myTypeEvalContext)
          val hasImplicitReturns =
            returnPoints.any { it !is PyReturnStatement }

          if (hasImplicitReturns) {
            val actual = node.getReturnStatementType(myTypeEvalContext)
            val annotationValue = if (annotation != null) annotation.value else node.typeComment
            if (annotationValue != null) {
              registerProblem(annotationValue, typeMismatchMessage(expected, actual),
                              effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING),
                              PyMakeFunctionReturnTypeQuickFix(node, myTypeEvalContext))
            }
          }
        }

        val annotatedType = myTypeEvalContext.getReturnType(node)

        if (isInitMethod(node) && !(returnsNone || annotatedType is PyNeverType)) {
          registerProblem(
            if (annotation != null) annotation.value else node.typeComment,
            PyPsiBundle.message("INSP.type.checker.init.should.return.none")
          )
        }

        if (node.isGenerator) {
          val generatorDesc = fromGeneratorOrProtocol(annotatedType, myTypeEvalContext)
          val shouldBeAsync = node.isAsync && node.isAsyncAllowed
          val wrongSyncAsync = generatorDesc != null && generatorDesc.isAsync != shouldBeAsync

          val inferredType = node.getInferredReturnType(myTypeEvalContext)
          if (wrongSyncAsync || (generatorDesc == null && !match(annotatedType, inferredType, myTypeEvalContext))) {
            val annotationValue = if (annotation != null) annotation.value else node.typeComment
            if (annotationValue != null) {
              registerProblem(annotationValue, typeMismatchMessage(inferredType, annotatedType),
                              ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                              PyMakeFunctionReturnTypeQuickFix(node, myTypeEvalContext))
            }
          }
        }
      }
    }

    override fun visitPyNamedParameter(node: PyNamedParameter) {
      if (!hasExplicitType(node)) return

      val defaultValue = flattenParens(node.defaultValue)
      if (defaultValue == null) return

      if (defaultValue is PyEllipsisLiteralExpression && (isProtocolMethodParameter(node) || isOverloadSignature(node))) {
        return
      }

      // we use `PyTypingTypeProvider.getType` of the annotation directly, instead of `node.getType`,
      //  because otherwise `PyTypingTypeProvider` will inject the type of `None`
      val expectedRef = PyTypingTypeProvider.getType(node.annotation!!.value!!, myTypeEvalContext)
      if (expectedRef == null) return
      val expected = expectedRef.get()
      val actual = tryPromotingType(defaultValue, expected)

      if (actual is PySentinelType) return

      if (!matchesExpectedType(expected, actual, defaultValue, null)) {
        registerProblem(
          defaultValue, typeMismatchMessage(expected, actual),
          effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        )
      }
    }

    private fun isProtocolMethodParameter(node: PyNamedParameter): Boolean {
      val parent = node.context
      if (parent is PyParameterList) {
        val containingCallable = parent.containingCallable
        if (containingCallable is PyFunction) {
          val containingClass = containingCallable.containingClass
          if (containingClass == null) {
            return false
          }
          val classType = myTypeEvalContext.getType(containingClass)
          if (classType is PyClassLikeType && classType.isProtocol(myTypeEvalContext)) {
            return true
          }
        }
      }
      return false
    }

    private fun isOverloadSignature(node: PyNamedParameter): Boolean {
      val parent = node.parent
      if (parent is PyParameterList) {
        val containingCallable = parent.containingCallable
        if (containingCallable is PyFunction) {
          return isOverload(containingCallable, myTypeEvalContext)
        }
      }
      return false
    }

    override fun visitPyComprehensionElement(node: PyComprehensionElement) {
      super.visitPyComprehensionElement(node)

      for (forComponent in node.forComponents) {
        checkIteratedValue(forComponent.getIteratedList(), forComponent.isAsync)
      }
    }

    private fun checkCallSite(callSite: PyCallSiteOwner) {
      val calleesResults = mapArguments(callSite, resolveContext)
        .filter { it.isComplete }
        .mapNotNull { analyzeCallee(callSite, it) }
        .toList()

      if (calleesResults.none { isMatched(it) }) {
        PyTypeCheckerInspectionProblemRegistrar
          .registerProblem(
            this, callSite, calleesResults, myTypeEvalContext,
            effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          )
      }
    }

    private fun checkIteratedValue(iteratedValue: PyExpression?, isAsync: Boolean): Boolean {
      return checkIteratedValue(iteratedValue, iteratedValue, isAsync)
    }

    private fun checkIteratedValue(iteratedValue: PyExpression?, highlightElement: PsiElement?, isAsync: Boolean): Boolean {
      if (iteratedValue == null || highlightElement == null) return false
      val type = myTypeEvalContext.getType(iteratedValue)
      val iterableClassName = if (isAsync) PyNames.ASYNC_ITERABLE else PyNames.ITERABLE

      if (type != null && !isUnknown(type, myTypeEvalContext) && !isSubtype(type, iterableClassName, myTypeEvalContext)) {
        val typeName = PythonDocumentationProvider.getTypeName(type, myTypeEvalContext)

        val qualifiedName = "collections.$iterableClassName"
        registerProblem(
          highlightElement,
          PyPsiBundle.problemMessage("INSP.type.checker.expected.type.got.type.instead", qualifiedName, typeName),
          effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        )
        return true
      }
      return false
    }

    private fun checkUnpackIterableValue(iteratedValue: PyExpression?): Boolean {
      var value: PyExpression? = iteratedValue ?: return false
      if (value is PyStarExpression) value = value.expression
      if (value == null) return false
      // A generic-class subscription like `*A[int]` is always iterable at runtime:
      // `types.GenericAlias.__iter__` yields the subscript args.
      if (value is PySubscriptionExpression) {
        val operandType = myTypeEvalContext.getType(value.operand)
        if (operandType is PyClassLikeType && operandType.isDefinition) {
          return false
        }
      }
      val type = myTypeEvalContext.getType(value)
      if (type != null && !isUnknown(type, myTypeEvalContext) && !isSubtype(type, PyNames.ITERABLE, myTypeEvalContext)) {
        val typeName = PythonDocumentationProvider.getTypeName(type, myTypeEvalContext)
        registerProblem(value, PyPsiBundle.message("INSP.type.checker.unpack.expected.iterable", typeName),
                        effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
        return true
      }
      return false
    }

    private fun checkUnpackMappingValue(mappingValue: PyExpression?) {
      var value: PyExpression? = mappingValue ?: return
      if (value is PyDoubleStarExpression) value = value.expression
      if (value == null) return
      val type = myTypeEvalContext.getType(value)
      if (type != null && !isUnknown(type, myTypeEvalContext) &&
          // TODO: it's not Mapping, but a more wider type
          !isSubtype(type, PyNames.MAPPING, myTypeEvalContext)) {
        val typeName = PythonDocumentationProvider.getTypeName(type, myTypeEvalContext)
        registerProblem(value, PyPsiBundle.message("INSP.type.checker.unpack.expected.mapping", typeName),
                        effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
      }
    }

    private fun targetOrResolvedHasExplicitType(target: PyTargetExpression): Boolean {
      var current: PsiElement = target
      while (current is PyTargetExpression) {
        if (hasExplicitType(current)) return true
        val resolved = current.getReference(PyResolveContext.defaultContext(myTypeEvalContext)).resolve()
        if (resolved === current || resolved == null) break
        current = resolved
      }
      return false
    }

    /**
     * Number of values produced by a tuple expression used as the right-hand side of an unpacking assignment.
     * A starred element contributes the length of its operand only when the operand is a statically known
     * (heterogeneous, bounded) tuple; if any starred operand has an indeterminate length, returns `-1`.
     */
    private fun getUnpackedTupleLength(rhsTuple: PyTupleExpression): Int {
      var count = 0
      for (element in rhsTuple.elements) {
        if (element is PyStarExpression) {
          val operand = element.expression ?: return -1
          val operandType = myTypeEvalContext.getType(operand)
          if (operandType !is PyTupleType || operandType.isHomogeneous) {
            return -1
          }
          count += operandType.elementCount
        }
        else {
          count++
        }
      }
      return count
    }

    private fun checkContextManagerValue(iteratedValue: PyExpression?, isAsync: Boolean) {
      if (iteratedValue != null) {
        val type = myTypeEvalContext.getType(iteratedValue)
        val contextManagerClassName = if (isAsync) PyNames.ABSTRACT_ASYNC_CONTEXT_MANAGER else PyNames.ABSTRACT_CONTEXT_MANAGER

        if (type != null && !isUnknown(type, myTypeEvalContext) && !isSubtype(type, contextManagerClassName, myTypeEvalContext)) {
          val typeName = PythonDocumentationProvider.getTypeName(type, myTypeEvalContext)

          val qualifiedName = "contextlib.$contextManagerClassName"
          registerProblem(
            iteratedValue,
            PyPsiBundle.problemMessage("INSP.type.checker.expected.type.got.type.instead", qualifiedName, typeName),
            effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          )
        }
      }
    }

    private fun analyzeCallee(
      callSite: PyCallSiteOwner,
      mapping: PyArgumentsMapping,
    ): AnalyzeCalleeResults? {
      val callableType = mapping.callableType
      if (callableType == null) return null

      val result = ArrayList<AnalyzeArgumentResult>()
      val unexpectedArgumentForParamSpecs = ArrayList<UnexpectedArgumentForParamSpec>()
      val unfilledParameterFromParamSpecs = ArrayList<UnfilledParameterFromParamSpec>()

      val receiver = callSite.getReceiver(callableType.callable)
      val substitutions = unifyReceiver(mapping, myTypeEvalContext)

      val selfParameter = mapping.implicitParameters.firstOrNull()
      if (receiver != null && selfParameter != null) {
        var actual = myTypeEvalContext.getType(receiver)
        // TODO (PY-89400): Support validation for `receiver` of a union type
        // When `receiver` has a union type, we must find the specific member of the union bound to `callableType`.
        // See `Py3TypeCheckerInspectionTest.testAnnotatedSelfAgainstUnionReceiver`.
        if (actual !is PyUnionType) {
          if (actual is PyInstantiableType<*>) {
            if (isConstructorCall(callSite) && isInitMethod(callableType.callable)) {
              actual = actual.toInstance()
            }
            if (callableType.modifier == PyAstFunction.Modifier.CLASSMETHOD) {
              actual = actual.toClass()
            }
          }

          val expected = selfParameter.getArgumentType(myTypeEvalContext)
          // Skip the check when `expected` is a metaclass-scoped `PySelfType`:
          // - explicit `typing.Self` usage on a metaclass is disallowed by the typing specification;
          // - for an unannotated `self`/`cls` (for which the inferred type is also `PySelfType`),
          //   the bound-receiver resolution already guarantees that the receiver is an instance of the metaclass;
          // - matching a class receiver against a metaclass-scoped `PySelfType` currently fails
          //   (see `Py3TypeCheckerInspectionTest.testSelfOnMetaclass`).
          var isSelfOnMetaclass = false
          if (expected is PySelfType) {
            val typeType = getInstance(callSite).typeType
            isSelfOnMetaclass = typeType != null &&
                                expected.scopeClassType.getAncestorTypes(myTypeEvalContext).contains(typeType.toClass())
          }
          if (!isSelfOnMetaclass) {
            if (!matchParameterAndArgument(expected, actual, receiver, substitutions)) {
              result.add(
                AnalyzeArgumentResult(
                  receiver,
                  selfParameter,
                  expected,
                  substituteGenerics(expected, substitutions),
                  actual,
                  false
                )
              )
            }
          }
        }
      }

      val mappedParameters = mapping.mappedParameters
      val regularMappedParameters =
        PyCallExpressionHelper.getRegularMappedParameters(mappedParameters)

      for (entry in regularMappedParameters.entries) {
        val argument: PyExpression = entry.key!!
        val parameter: PyCallableParameter = entry.value
        val expected = parameter.getArgumentType(myTypeEvalContext)
        val promotedToLiteral = promoteToLiteral(argument, expected, myTypeEvalContext, substitutions)
        val actual = promotedToLiteral ?: myTypeEvalContext.getType(argument)

        if (expected is PyParamSpecType) {
          val allArguments = callSite.getArguments(callableType.callable)
          analyzeParamSpec(
            expected, allArguments, substitutions, result, unexpectedArgumentForParamSpecs,
            unfilledParameterFromParamSpecs
          )
          break
        }
        else if (expected is PyConcatenateType) {
          val allArguments = callSite.getArguments(callableType.callable)
          if (allArguments.isEmpty()) break

          val firstExpectedTypes = expected.firstTypes
          var nonStarCount = 0
          for (arg in allArguments) {
            if (arg is PyStarArgument) break
            nonStarCount++
          }
          val argumentRightBound = min(firstExpectedTypes.size, nonStarCount)
          val firstArguments = allArguments.subList(0, argumentRightBound)
          matchArgumentsAndTypes(firstArguments, firstExpectedTypes, substitutions, result)

          val paramSpec = expected.paramSpec
          val restArguments = allArguments.subList(argumentRightBound, allArguments.size)
          if (paramSpec != null) {
            if (argumentRightBound < firstExpectedTypes.size) {
              // Not enough positional arguments to satisfy the Concatenate prefix, e.g., int, str in Concatenate[int, str, P]
              val paramSpecSubst: PyCallableParameterListType? = getParamSpecSubstitution(paramSpec, substitutions)
              if (paramSpecSubst == null) {
                for (arg in restArguments) {
                  if (arg is PyStarArgument) {
                    unexpectedArgumentForParamSpecs.add(UnexpectedArgumentForParamSpec(arg, paramSpec))
                    break
                  }
                }
              }
            }
            analyzeParamSpec(
              paramSpec, restArguments, substitutions, result, unexpectedArgumentForParamSpecs,
              unfilledParameterFromParamSpecs
            )
          }

          break
        }
        else {
          val matched = matchParameterAndArgument(expected, actual, argument, substitutions)
          result.add(AnalyzeArgumentResult(argument, parameter, expected, substituteGenerics(expected, substitutions), actual, matched))
        }
      }

      val positionalContainer = PyCallExpressionHelper.getMappedPositionalContainer(mappedParameters)
      val positionalArguments = PyCallExpressionHelper.getArgumentsMappedToPositionalContainer(mappedParameters)
      val keywordContainer = PyCallExpressionHelper.getMappedKeywordContainer(mappedParameters)
      val keywordArguments = PyCallExpressionHelper.getArgumentsMappedToKeywordContainer(mappedParameters)
      val allArguments = positionalArguments + keywordArguments

      val paramSpecType = getParamSpecTypeFromContainerParameters(keywordContainer, positionalContainer)
      if (paramSpecType != null) {
        // Keyword arguments for positional parameters preceding *args: P.args
        // might shadow the values in ParamSpec, causing runtime errors. Report them when P is unsubstituted.
        val paramSpecSubst: PyCallableParameterListType? = getParamSpecSubstitution(paramSpecType, substitutions)
        if (paramSpecSubst == null) {
          for (entry in regularMappedParameters.entries) {
            if (entry.key is PyKeywordArgument) {
              unexpectedArgumentForParamSpecs.add(UnexpectedArgumentForParamSpec(entry.key, paramSpecType))
            }
          }
        }
        analyzeParamSpec(
          paramSpecType, allArguments, substitutions, result, unexpectedArgumentForParamSpecs,
          unfilledParameterFromParamSpecs
        )
      }
      else {
        if (positionalContainer != null) {
          result.addAll(analyzeContainerMapping(positionalContainer, positionalArguments, substitutions))
        }
        if (keywordContainer != null) {
          result.addAll(analyzeContainerMapping(keywordContainer, keywordArguments, substitutions))
        }
      }

      val unfilledPositionalVarargs = ArrayList<UnfilledPositionalVararg>()
      for (unmappedContainer in mapping.unmappedContainerParameters) {
        val containerType = unmappedContainer.getArgumentType(myTypeEvalContext)
        if (unmappedContainer.name == null || containerType !is PyPositionalVariadicType) continue
        val expandedVararg = substitute(containerType, substitutions, myTypeEvalContext)
        if (expandedVararg !is PyUnpackedTupleType || expandedVararg.isUnbound) continue
        if (expandedVararg.elementTypes.isEmpty()) continue
        if (expandedVararg.elementTypes.all { it is PyPositionalVariadicType }
        ) continue
        unfilledPositionalVarargs.add(
          UnfilledPositionalVararg(
            unmappedContainer.name!!,
            PythonDocumentationProvider.getTypeName(expandedVararg, myTypeEvalContext)
          )
        )
      }

      return AnalyzeCalleeResults(
        callableType, callableType.callable, result,
        unexpectedArgumentForParamSpecs,
        unfilledParameterFromParamSpecs,
        unfilledPositionalVarargs
      )
    }

    private fun isConstructorCall(callSite: PyCallSiteOwner): Boolean {
      if (callSite is PyCallExpression) {
        val callee = callSite.callee
        if (callee != null && (myTypeEvalContext.getType(callee) as? PyClassType)?.isDefinition == true) {
          return true
        }
      }
      return false
    }

    private fun analyzeParamSpec(
      paramSpec: PyParamSpecType, arguments: List<PyExpression>,
      substitutions: GenericSubstitutions,
      result: MutableList<AnalyzeArgumentResult>,
      unexpectedArgumentForParamSpecs: MutableList<UnexpectedArgumentForParamSpec>,
      unfilledParameterFromParamSpecs: MutableList<UnfilledParameterFromParamSpec>,
    ) {
      val paramSpecSubst: PyCallableParameterListType? = getParamSpecSubstitution(paramSpec, substitutions)
      if (paramSpecSubst == null) {
        analyzeUnsubstitutedParamSpec(paramSpec, arguments, unexpectedArgumentForParamSpecs)
        return
      }

      val mapping = analyzeArguments(arguments, paramSpecSubst, myTypeEvalContext)
      for (item in mapping.mappedParameters.entries) {
        val argument = item.key
        val parameter = item.value
        val argType = myTypeEvalContext.getType(argument)
        val paramType = parameter.getType(myTypeEvalContext)
        val matched = matchParameterAndArgument(paramType, argType, argument, substitutions)
        result.add(AnalyzeArgumentResult(argument, parameter, paramType, substituteGenerics(paramType, substitutions), argType, matched))
      }
      if (!mapping.unmappedArguments.isEmpty()) {
        for (argument in mapping.unmappedArguments) {
          unexpectedArgumentForParamSpecs.add(UnexpectedArgumentForParamSpec(argument!!, paramSpec))
        }
      }
      val unmappedParameters = mapping.unmappedParameters
      if (!unmappedParameters.isEmpty()) {
        unfilledParameterFromParamSpecs.add(UnfilledParameterFromParamSpec(unmappedParameters[0]!!, paramSpec))
      }
    }

    private fun analyzeUnsubstitutedParamSpec(
      paramSpec: PyParamSpecType,
      arguments: List<PyExpression>,
      unexpectedArgs: MutableList<UnexpectedArgumentForParamSpec>,
    ) {
      for (argument in arguments) {
        if (argument is PyStarArgument) {
          val innerExpr = argument.expression
          if (innerExpr != null && isParamSpecContainerForwarding(innerExpr, paramSpec, !argument.isKeyword)) {
            continue
          }
        }
        unexpectedArgs.add(UnexpectedArgumentForParamSpec(argument, paramSpec))
      }
    }

    private fun isParamSpecContainerForwarding(
      expr: PyExpression,
      paramSpec: PyParamSpecType,
      expectPositional: Boolean,
    ): Boolean {
      val type = myTypeEvalContext.getType(expr)
      if (type !is PyParamSpecType || type != paramSpec) {
        return false
      }
      if (expr is PyReferenceExpression) {
        val resolved = expr.reference.resolve()
        if (resolved is PyNamedParameter) {
          return if (expectPositional) resolved.isPositionalContainer else resolved.isKeywordContainer
        }
      }
      return true
    }

    private fun matchArgumentsAndTypes(
      arguments: List<PyExpression>, types: List<PyType?>,
      substitutions: GenericSubstitutions,
      result: MutableList<AnalyzeArgumentResult>,
    ) {
      val size = min(arguments.size, types.size)
      for (i in 0..<size) {
        val expected = types[i]
        val argument = arguments[i]
        val actual = myTypeEvalContext.getType(argument)
        val matched = matchParameterAndArgument(expected, actual, argument, substitutions)
        result.add(AnalyzeArgumentResult(argument, null, expected, substituteGenerics(expected, substitutions), actual, matched))
      }
    }

    private fun analyzeContainerMapping(
      container: PyCallableParameter,
      arguments: List<PyExpression>,
      substitutions: GenericSubstitutions,
    ): List<AnalyzeArgumentResult> {
      val expected = container.getArgumentType(myTypeEvalContext)

      if (container.isPositionalContainer && expected is PyPositionalVariadicType) {
        val argumentTypes = PyUnpackedTupleTypeImpl.create(
          arguments.map { myTypeEvalContext.getType(it) }
        )
        val matched = matchParameterAndArgument(expected, argumentTypes, null, substitutions)
        return arguments.map { argument ->
          val expectedWithSubstitutions = substituteGenerics(expected, substitutions)
          AnalyzeArgumentResult(argument, container, expected, expectedWithSubstitutions, argumentTypes, matched)
        }
      }

      // For an expected type with generics we have to match all the actual types against it in order to do proper generic unification
      if (expected.hasGenerics(myTypeEvalContext)) {
        // First collect type parameter substitutions by matching the expected type with the union, if it's a keyword container
        // otherwise, match as usual arguments, passed to a function
        if (container.isKeywordContainer) {
          val actualJoin = PyUnionType.union(
            arguments.map { myTypeEvalContext.getType(it) }
          )
          matchParameterAndArgument(expected, actualJoin, null, substitutions)
        }
        return arguments.map {
          // Then match each argument type against the expected type after these substitutions.
          val actual = myTypeEvalContext.getType(it)
          val matched = matchParameterAndArgument(expected, actual, it, substitutions)
          AnalyzeArgumentResult(it, container, expected, substituteGenerics(expected, substitutions), actual, matched)
        }
      }
      else {
        return arguments.map { argument ->
          val promotedToLiteral =
            promoteToLiteral(argument, expected, myTypeEvalContext, substitutions)
          val actual = promotedToLiteral ?: myTypeEvalContext.getType(argument)
          val matched = matchParameterAndArgument(expected, actual, argument, substitutions)
          val expectedWithSubstitutions = substituteGenerics(expected, substitutions)
          AnalyzeArgumentResult(argument, container, expected, expectedWithSubstitutions, actual, matched)
        }
      }
    }

    private fun getParamSpecTypeFromContainerParameters(
      positionalContainer: PyCallableParameter?,
      keywordContainer: PyCallableParameter?,
    ): PyParamSpecType? {
      if (positionalContainer == null && keywordContainer == null) return null
      val container = positionalContainer ?: keywordContainer
      return container!!.getType(myTypeEvalContext) as? PyParamSpecType
    }

    private fun matchParameterAndArgument(
      parameterType: PyType?,
      argumentType: PyType?,
      argument: PyExpression?,
      substitutions: GenericSubstitutions,
    ): Boolean {
      var argument = argument
      argument = peelArgument(argument)

      if (argument != null) {
        if (isDictExpression(argument, myTypeEvalContext) &&
            parameterType is PyTypedDictType
        ) {
          reportTypedDictProblems(parameterType, argument)
          return true
        }
        else if (parameterType is PyUnpackedTypedDictType) {
          reportUnpackedTypedDictProblems(parameterType, argument)
          return true
        }
      }

      return matchesExpectedType(parameterType, argumentType, argument, substitutions)
             && !matchingProtocolDefinitions(parameterType, argumentType, myTypeEvalContext)
    }

    private fun substituteGenerics(
      expectedArgumentType: PyType?,
      substitutions: GenericSubstitutions,
    ): PyType? {
      return if (expectedArgumentType.hasGenerics(myTypeEvalContext))
        substitute(expectedArgumentType, substitutions, myTypeEvalContext)
      else
        null
    }

    companion object {
      fun getExpectedReturnStatementType(function: PyFunction, typeEvalContext: TypeEvalContext): PyType? {
        val returnType = typeEvalContext.getReturnType(function)
        if (function.isGenerator) {
          val generatorDesc = fromGeneratorOrProtocol(returnType, typeEvalContext)
          if (generatorDesc != null) {
            return generatorDesc.returnType
          }
          return unknown
        }
        if (function.isAsync) {
          return coroutineOrGeneratorElementType(returnType).derefOrUnknown()
        }
        return returnType
      }

      private fun requiresTypeSpecialization(type: PyType?): Boolean {
        if (type is PyTypeParameterType && type.defaultType == null && (type !is PySelfType)) {
          return true
        }
        return type is PyCollectionType &&
               type.elementTypes.any { requiresTypeSpecialization(it) }
      }

      private fun getParamSpecSubstitution(
        paramSpecType: PyParamSpecType,
        substitutions: GenericSubstitutions,
      ): PyCallableParameterListType? {
        return substitutions.paramSpecs[paramSpecType] as? PyCallableParameterListType
      }

      private fun isMatched(calleeResults: AnalyzeCalleeResults): Boolean {
        return calleeResults.results.all { it.isMatched } &&
               calleeResults.unmatchedArguments.isEmpty() &&
               calleeResults.unmatchedParameters.isEmpty() &&
               calleeResults.unfilledPositionalVarargs.isEmpty()
      }

      private fun hasExplicitType(node: PsiElement): Boolean {
        if (node is PyAnnotationOwner && node.annotation != null) return true
        if (node is PyTypeCommentOwner && node.typeCommentAnnotation != null) return true
        return false
      }
    }
  }

  override fun inspectionFinished(session: LocalInspectionToolSession, problemsHolder: ProblemsHolder) {
    if (LOG.isDebugEnabled) {
      val startTime = session.getUserData(TIME_KEY)
      if (startTime != null) {
        LOG.debug(
          String.format(
            "[%d] elapsed time: %d ms\n",
            Thread.currentThread().id,
            (System.nanoTime() - startTime) / 1000000
          )
        )
      }
    }
  }

  internal class AnalyzeCalleeResults(
    val callableType: PyCallableType,
    val callable: PyCallable?,
    val results: List<AnalyzeArgumentResult>,
    val unmatchedArguments: List<UnexpectedArgumentForParamSpec>,
    val unmatchedParameters: List<UnfilledParameterFromParamSpec>,
    val unfilledPositionalVarargs: List<UnfilledPositionalVararg>,
  )

  internal class AnalyzeArgumentResult(
    val argument: PyExpression,
    val parameter: PyCallableParameter?,
    val expectedType: PyType?,
    val expectedTypeAfterSubstitution: PyType?,
    val actualType: PyType?,
    val isMatched: Boolean,
  )

  internal class UnfilledParameterFromParamSpec(val parameter: PyCallableParameter, val paramSpecType: PyParamSpecType)

  internal class UnexpectedArgumentForParamSpec(val argument: PyExpression, val paramSpecType: PyParamSpecType)

  @JvmRecord
  internal data class UnfilledPositionalVararg(@JvmField val varargName: String, @JvmField val expectedTypes: String)
  companion object {
    private val LOG = thisLogger()
    private val TIME_KEY = Key.create<Long>("PyTypeCheckerInspection.StartTime")
  }
}
