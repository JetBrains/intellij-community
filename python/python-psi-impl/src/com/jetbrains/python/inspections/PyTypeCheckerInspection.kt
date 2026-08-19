// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.childOfType
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
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
import com.jetbrains.python.inspections.PyInspectionMessages.CodifiedParam
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
import com.jetbrains.python.psi.PyQualifiedElement
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
import com.jetbrains.python.psi.impl.PyReferenceExpressionImpl
import com.jetbrains.python.psi.impl.PySubscriptionExpressionImpl
import com.jetbrains.python.psi.impl.PyTargetExpressionImpl
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.search.PySuperMethodsSearch
import com.jetbrains.python.psi.types.PyABCUtil.isSubtype
import com.jetbrains.python.psi.types.PyAnyType
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.PyCallableParameterListType
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyCollectionTypeImpl
import com.jetbrains.python.psi.types.PyConcatenateType
import com.jetbrains.python.psi.types.PyDescriptorTypeUtil.getExpectedValueTypeForDunderSet
import com.jetbrains.python.psi.types.PyLiteralType.Companion.promoteToLiteral
import com.jetbrains.python.psi.types.PyLiteralType.Companion.upcastLiteralToClass
import com.jetbrains.python.psi.types.PyNeverType
import com.jetbrains.python.psi.types.PyParamSpecType
import com.jetbrains.python.psi.types.PyPositionalVariadicType
import com.jetbrains.python.psi.types.PySelfType
import com.jetbrains.python.psi.types.PySentinelType
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeChecker.GenericSubstitutions
import com.jetbrains.python.psi.types.PyTypeChecker.containsAny
import com.jetbrains.python.psi.types.PyTypeChecker.explainMismatch
import com.jetbrains.python.psi.types.PyTypeChecker.getTargetTypeFromTupleAssignment
import com.jetbrains.python.psi.types.PyTypeChecker.hasGenerics
import com.jetbrains.python.psi.types.PyTypeChecker.isUnknown
import com.jetbrains.python.psi.types.PyTypeChecker.match
import com.jetbrains.python.psi.types.PyTypeChecker.substitute
import com.jetbrains.python.psi.types.PyTypeChecker.unifyReceiver
import com.jetbrains.python.psi.types.PyTypeInferenceCspFactory.unifyReceiver
import com.jetbrains.python.psi.types.PyTypeMismatchExplanation
import com.jetbrains.python.psi.types.PyTypeParameterMapping
import com.jetbrains.python.psi.types.PyTypeParameterType
import com.jetbrains.python.psi.types.PyTypeUtil.asUnionSequence
import com.jetbrains.python.psi.types.PyTypeUtil.compositeComponents
import com.jetbrains.python.psi.types.PyTypeUtil.compositeMap
import com.jetbrains.python.psi.types.PyTypeUtil.derefOrUnknown
import com.jetbrains.python.psi.types.PyTypeUtil.getCallableItems
import com.jetbrains.python.psi.types.PyTypedDictType
import com.jetbrains.python.psi.types.PyTypedDictType.Companion.checkExpression
import com.jetbrains.python.psi.types.PyTypedDictType.Companion.isDictExpression
import com.jetbrains.python.psi.types.PyTypedDictType.TypeCheckingResult
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.PyUnpackedTupleType
import com.jetbrains.python.psi.types.PyUnpackedTupleTypeImpl
import com.jetbrains.python.psi.types.PyUnpackedTypedDictType
import com.jetbrains.python.psi.types.PyUnsafeUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.isAnyOrUnknown
import com.jetbrains.python.psi.types.isNoneType
import com.jetbrains.python.psi.types.isObject
import com.jetbrains.python.psi.types.isUnknown
import com.jetbrains.python.pyi.PyiUtil.isOverload
import org.jetbrains.annotations.PropertyKey
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
    override val holder = super.holder!!

    private val typedDictProblemReporter = TypedDictProblemReporter()

    // TODO: Visit decorators with arguments
    override fun visitPyCallExpression(node: PyCallExpression) {
      checkCallSite(node)
    }

    // A class definition implicitly calls `__init_subclass__` of its base classes with the
    // class-definition keyword arguments; type-check those arguments against its parameters.
    override fun visitPyClass(node: PyClass) {
      if (node.getArguments(null).isEmpty()) return
      checkCallSite(node)
    }

    override fun visitPyBinaryExpression(node: PyBinaryExpression) {
      checkCallSite(node)
    }

    override fun visitPyAugAssignmentStatement(node: PyAugAssignmentStatement) {
      checkCallSite(node)
      // Most of the following follows the logic of `visitPyTargetExpression` taking into account that
      // an augmented assignment target is normally a reference.
      val target = node.target
      if (target !is PyReferenceExpression) return

      var expected = myTypeEvalContext.getType(target)
      val resolved: PsiElement? = target.getReference(PyResolveContext.defaultContext(myTypeEvalContext)).resolve()
      if (resolved !is PyTargetExpression || !hasExplicitType(resolved)) return

      val qualifier = target.qualifier
      if (qualifier != null) {
        expected = myTypeEvalContext.getType(qualifier).compositeMap {
          val substitutions = unifyReceiver(it, myTypeEvalContext)
          substitute(expected, substitutions, myTypeEvalContext)
        }
      }

      var isDescriptor = false
      val classAttrType = getClassAttributeType(target)
      if (classAttrType != null) {
        val dunderSetValueType =
          getExpectedValueTypeForDunderSet(target, classAttrType.get(), myTypeEvalContext)
        if (dunderSetValueType != null) {
          expected = dunderSetValueType.get()
          isDescriptor = true
        }
      }
      // This gives the result type for calling the corresponding `__iXXX__` "inplace" method or the normal operator method.
      val actual = node.getType(myTypeEvalContext)
      if (!matchesExpectedType(expected, actual, null, null)) {
        val message =
          if (isDescriptor) {
            typeMismatchMessage(
              expected,
              actual,
              node,
              "INSP.type.checker.expected.type.from.dunder.set.got.type.instead"
            )
          }
          else {
            typeMismatchMessage(
              expected,
              actual,
              node,
              "INSP.type.checker.expected.type.from.aug.assignment.got.type.instead"
            )
          }
        registerTypeMismatch(PyTypeCheckerSuppressionCode.BAD_ASSIGNMENT, node, expected, actual, message,
                             effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
      }
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
            PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.BAD_INDEX, indexExpression,
                                                PyPsiBundle.message("INSP.type.checker.tuple.index.out.of.range"))
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
      if (checkIteratedValue(node.forPart.source, node.isAsync)) return
      checkForTargetUnpacking(node)
    }

    private fun checkForTargetUnpacking(node: PyForStatement) {
      val forPart = node.forPart
      val target = flattenParens(forPart.target)
      if (target !is PyTupleExpression && target !is PyListLiteralExpression) return
      val source = forPart.source ?: return
      val sourceType = myTypeEvalContext.getType(source) ?: return
      if (sourceType.containsAny(context = myTypeEvalContext)) return
      val itemType = PyTargetExpressionImpl.getIterationType(sourceType, source, node.isAsync, myTypeEvalContext)
      if (!itemType.isAnyOrUnknown && !itemType.containsAny(context = myTypeEvalContext) &&
          !isSubtype(itemType, PyNames.ITERABLE, myTypeEvalContext)) {
        registerProblem(target,
                        PyPsiBundle.problemMessage("INSP.type.checker.unpack.expected.iterable",
                                            CodifiedParam.ofType(itemType, target, myTypeEvalContext)),
                        effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
        return
      }
      if (itemType is PyTupleType && !itemType.isHomogeneous) {
        checkNestedUnpackingBalance(target.elements, itemType)
      }
      if (itemType != null) checkUnpackedTargetTypes(target, itemType, target)
    }

    private fun checkUnpackedTargetTypes(targetSeq: PySequenceExpression, valueType: PyType, highlight: PsiElement) {
      if (valueType.containsAny(context = myTypeEvalContext)) return
      for (target in targetSeq.elements) {
        val leaf = flattenParens(target)
        if (leaf !is PyTargetExpression) continue
        val annotatedType = resolvedDeclaredType(leaf) ?: continue
        if (annotatedType.containsAny(context = myTypeEvalContext)) continue
        val received = if (valueType is PyTupleType && !valueType.isHomogeneous) {
          getTargetTypeFromTupleAssignment(leaf, targetSeq, valueType, myTypeEvalContext)
        }
        else {
          (valueType as? PyClassType)?.iteratedItemType
        } ?: continue
        if (received.containsAny(context = myTypeEvalContext)) continue
        val actual = upcastLiteralToClass(received)
        if (!match(annotatedType, actual, myTypeEvalContext)) {
          registerProblem(highlight,
                          PyPsiBundle.problemMessage("INSP.type.checker.expected.type.got.type.instead",
                                                     CodifiedParam.ofType(annotatedType, highlight, myTypeEvalContext, verbose = true),
                                                     CodifiedParam.ofType(actual, highlight, myTypeEvalContext)),
                          effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
          // report only the first mismatch per value
          return
        }
      }
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
            PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.BAD_RETURN, returnExpr ?: node,
                                                typeMismatchMessage(expected, actual, returnExpr ?: node),
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
        PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.BAD_RETURN, yieldExpr,
                                            PyPsiBundle.problemMessage("INSP.type.checker.yield.from.async.generator",
                                                                       CodifiedParam.ofType(delegateType, yieldExpr, myTypeEvalContext)))
        return
      }

      if (checkIteratedValue(yieldExpr, false)) return

      val annotatedGeneratorDesc = getGeneratorDescriptorFromAnnotation(function, node)
      if (annotatedGeneratorDesc == null) return

      if (checkYieldType(annotatedGeneratorDesc.yieldType, node, function)) return

      // Reversed because SendType is contravariant
      val expectedSendType = annotatedGeneratorDesc.sendType
      if (delegateDesc != null && !match(delegateDesc.sendType, expectedSendType, myTypeEvalContext)) {
        PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.BAD_RETURN, yieldExpr,
                                            PyPsiBundle.problemMessage(
                                              "INSP.type.checker.yield.from.send.type.mismatch",
                                              CodifiedParam.ofType(expectedSendType, yieldExpr, myTypeEvalContext, verbose = true),
                                              CodifiedParam.ofType(delegateDesc.sendType, yieldExpr, myTypeEvalContext)))
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
          registerTypeMismatch(PyTypeCheckerSuppressionCode.BAD_RETURN, yieldExpr, annotatedReturnType, inferredReturnType,
                               typeMismatchMessage(annotatedReturnType, inferredReturnType, yieldExpr),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               LocalQuickFix.from(PyMakeFunctionReturnTypeQuickFix(function, myTypeEvalContext))!!)
        }
        return null
      }
      return annotatedGeneratorDesc
    }

    private fun checkYieldType(expectedYieldType: PyType?, node: PyYieldExpression, function: PyFunction): Boolean {
      val thisYieldType = node.getYieldType(myTypeEvalContext)
      if (!matchesExpectedType(expectedYieldType, thisYieldType, node.expression, null)) {
        val yieldExpr = node.expression
        val anchor = yieldExpr ?: node
        registerTypeMismatch(PyTypeCheckerSuppressionCode.BAD_RETURN, anchor, expectedYieldType, thisYieldType,
                             PyPsiBundle.problemMessage(
                               "INSP.type.checker.yield.type.mismatch",
                               CodifiedParam.ofType(expectedYieldType, anchor, myTypeEvalContext, verbose = true),
                               CodifiedParam.ofType(thisYieldType, anchor, myTypeEvalContext)),
                             effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING),
                             LocalQuickFix.from(PyMakeFunctionReturnTypeQuickFix(function, myTypeEvalContext))!!)
        return true
      }
      return false
    }

    override fun visitPyReferenceExpression(node: PyReferenceExpression) {
      checkClassAttributeAccess(node)

      // Recompute the qualified reference type when it's `Unknown` to collect possible method binding errors.
      if (node.isQualified &&
          myTypeEvalContext.getType(node).asUnionSequence().any { it.isUnknown }) {
        val errors = mutableListOf<PyInspectionMessages.ProblemMessage>()
        PyReferenceExpressionImpl.getQualifiedReferenceType(node, myTypeEvalContext, errors)
        for (error in errors) {
          registerProblem(node, error, effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
        }
      }
    }

    override fun visitPyAssignmentStatement(node: PyAssignmentStatement) {
      val lhs = flattenParens(node.leftHandSideExpression)
      val rhs = node.assignedValue
      if ((lhs !is PyTupleExpression && lhs !is PyListLiteralExpression) || rhs == null) return
      val lhsSeq: PySequenceExpression = lhs

      // Check that the RHS is iterable
      if (checkUnpackIterableValue(rhs)) return

      val rhsType = myTypeEvalContext.getType(rhs)
      if (rhsType !is PyTupleType || rhsType.isHomogeneous) {
        // Non-tuple/homogeneous iterable: the balance is unknown, so only validate the annotated targets' types.
        if (rhsType != null) checkUnpackedTargetTypes(lhsSeq, rhsType, rhs)
        return
      }

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
      if (rhsCount >= 0 && checkUnpackBalance(targets.size, lhsStarCount, rhsCount, rhs, lhs,
                                              CodifiedParam.ofType(rhsType, rhs, myTypeEvalContext))) return

      checkNestedUnpackingBalance(targets, rhsType)

      // Per-element type mismatch check for annotated targets with a non-tuple RHS (`x, y = expr` / `[x] = expr`).
      // findAssignedValue() yields a synthetic subscription for these, so the mismatch is reported on the RHS itself.
      // A tuple RHS (`x, y = 1, 2` / `[x] = 1, 2`) maps to real value elements and is handled by
      // visitPyTargetExpression via findAssignedValue().
      if (lhsStarCount == 0 && rhs !is PyTupleExpression) {
        for (target in targets) {
          if (target !is PyTargetExpression) continue
          if (!targetOrResolvedHasExplicitType(target)) continue
          val annotatedType = myTypeEvalContext.getType(target)
          val unpackedType = getTargetTypeFromTupleAssignment(target, lhsSeq, rhsType, myTypeEvalContext) ?: continue
          if (match(annotatedType, unpackedType, myTypeEvalContext)) continue
          val displayType = upcastLiteralToClass(unpackedType)
          PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.BAD_ASSIGNMENT, rhs,
                                              typeMismatchMessage(annotatedType, displayType, rhs),
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
              if (rhsType is PyClassType && rhsType.isParameterized) {
                val elementType = upcastLiteralToClass(rhsType.iteratedItemType)
                val listClass = getInstance(node).getClass("list")
                if (listClass != null) {
                  val actualType = PyCollectionTypeImpl(listClass, false, listOf(elementType))
                  val annotatedType = myTypeEvalContext.getType(innerExpr)
                  if (annotatedType != null && !isUnknown(annotatedType, myTypeEvalContext) &&
                      !match(annotatedType, actualType, myTypeEvalContext)) {
                    PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.BAD_ASSIGNMENT, rhs,
                                                        typeMismatchMessage(annotatedType, actualType, rhs),
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
        // `assignedValueType` is the member value type produced by the enum's metaclass/constructor. For enums that
        // transform the declaration (e.g. Django's `ChoicesType.__new__` drops a trailing label), the type provider
        // already stripped the extra elements, so a plain match here is correct for both stdlib and framework enums.
        val actual = info.assignedValueType
        if (!match(expected, actual, myTypeEvalContext)) {
          registerTypeMismatch(PyTypeCheckerSuppressionCode.BAD_ASSIGNMENT, assignedValue, expected, actual,
                               typeMismatchMessage(expected, actual, assignedValue),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        }
        return
      }

      // We don't report type errors on non-annotated assignments inside class bodies because there
      // the expected attribute type is either:
      // - just the type of the assigned value, so there is nothing to type check against
      // - special-cased for some metaprogramming API, e.g., Django models,
      //    so the type provided by a dedicated PyTypeProvider intentionally differs from the type of the assigned value
      //    (e.g. str instead of TextField), then type checking it normally will cause a false positive.
      if (scopeOwner is PyClass && !targetOrResolvedHasExplicitType(node)) {
        return
      }

      // `T = typing.NewType('T', int)`
      if (!node.isQualified && assignedValue is PyCallExpression) {
        val calleeType = assignedValue.callee?.let { myTypeEvalContext.getType(it) }
        if (calleeType is PyClassLikeType && calleeType.classQName == PyTypingTypeProvider.NEW_TYPE) {
          return
        }
      }

      var expected = myTypeEvalContext.getType(node)
      val qualifier = node.qualifier
      if (qualifier != null) {
        expected = myTypeEvalContext.getType(qualifier).compositeMap {
          val substitutions = unifyReceiver(it, myTypeEvalContext)
          substitute(expected, substitutions, myTypeEvalContext)
        }
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
        // `tryPromotingType` may preserve element literals in a collection value (e.g. `list[Literal[1]]`),
        // which spuriously fails the invariant match against the value's own widened type (`list[int]`),
        // for instance for an un-annotated `x = [1, [1]]`. If the value's natural (un-promoted) type already
        // matches the expected type, the fresh literal is assignable and there is no real mismatch.
        // temporary special casing to avoid Literal problems PY-90366
        val naturalType = myTypeEvalContext.getType(assignedValue)
        if (naturalType != actual && matchesExpectedType(expected, naturalType, assignedValue, null)) {
          return
        }
        val message = if (isDescriptor) {
          typeMismatchMessage(expected, actual, assignedValue, "INSP.type.checker.expected.type.from.dunder.set.got.type.instead")
        }
        else {
          typeMismatchMessage(expected, actual, assignedValue)
        }
        registerTypeMismatch(PyTypeCheckerSuppressionCode.BAD_ASSIGNMENT, assignedValue, expected, actual, message,
                             effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
      }
    }

    private fun typeMismatchMessage(
      expected: PyType?,
      actual: PyType?,
      anchor: PsiElement,
    ): PyInspectionMessages.ProblemMessage {
      return typeMismatchMessage(expected, actual, anchor, "INSP.type.checker.expected.type.got.type.instead")
    }

    private fun typeMismatchMessage(
      expected: PyType?,
      actual: PyType?,
      anchor: PsiElement,
      @PropertyKey(resourceBundle = PyPsiBundle.BUNDLE) messageKey: @PropertyKey(resourceBundle = PyPsiBundle.BUNDLE) String,
    ): PyInspectionMessages.ProblemMessage {
      val base = PyPsiBundle.problemMessage(
        messageKey,
        CodifiedParam.ofType(expected, anchor, myTypeEvalContext, verbose = true),
        CodifiedParam.ofType(actual, anchor, myTypeEvalContext),
      )
      val diff = PyTypeDiff.diffTooltip(expected, actual, myTypeEvalContext)
      return if (diff != null) base.copy(tooltip = diff) else base
    }

    /**
     * Registers a type-mismatch problem. On-the-fly the editor-hover tooltip is the [message]'s own tooltip — the
     * aligned [PyTypeDiff] for structural mismatches — with the breakdown ([PyTypeChecker.explainMismatch]) appended
     * below it. The one-line [message] description stays flat, so batch results and existing golden tests are
     * unaffected; only the editor hover shows the diff and breakdown.
     */
    private fun registerTypeMismatch(
      code: PyTypeCheckerSuppressionCode,
      element: PsiElement?,
      expected: PyType?,
      actual: PyType?,
      message: PyInspectionMessages.ProblemMessage,
      type: ProblemHighlightType,
      vararg fixes: LocalQuickFix,
    ) {
      // Description stays the plain one-liner; on-the-fly the tooltip is the rich breakdown (built from the
      // enriched [message]). reportWithTooltip runs the supplier only on-the-fly, since it re-runs the match,
      // and tags the problem with [code] so it can be suppressed per-category.
      PyTypeCheckerProblemReporter.reportWithTooltip(holder, code, element, message, type, *fixes)
                                  { PyTypeCheckerInspectionProblemRegistrar.breakdownTooltip(message, expected, actual, myTypeEvalContext, element) }
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
      if (expectedSubst is PyClassType && expectedSubst.isParameterized && actualSubst is PyClassType && actualSubst.isParameterized) {
        val expClassType = expectedSubst.pyClass.getType(myTypeEvalContext)
        val actClassType = actualSubst.pyClass.getType(myTypeEvalContext)
        val isCreational = expExpr is PySequenceExpression
                           || expExpr is PyCallExpression && expExpr.callee !is PySubscriptionExpression
                           || expExpr is PyParenthesizedExpression && expExpr.containedExpression is PyTupleExpression
        val paramMapping = PyTypeParameterMapping.mapByShape(
          expectedSubst.typeArguments,
          actualSubst.typeArguments,
          PyTypeParameterMapping.Option.USE_DEFAULTS
        )
        if (isCreational && paramMapping != null && match(expClassType, actClassType, myTypeEvalContext)) {
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
      result.valueTypeErrors.forEach { error ->
        val actualExpression = error.actualExpression ?: return@forEach
        typedDictProblemReporter.report(
          holder,
          PyTypeCheckerSuppressionCode.BAD_TYPED_DICT,
          actualExpression,
          typeMismatchMessage(error.expectedType, error.actualType, actualExpression),
          effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        )
      }
      result.extraKeys.forEach { error ->
        typedDictProblemReporter.report(
          holder,
          PyTypeCheckerSuppressionCode.BAD_TYPED_DICT_KEY,
          error.actualExpression ?: expression,
          PyPsiBundle.problemMessage("INSP.type.checker.typed.dict.extra.key", error.key, error.expectedTypedDictName)
        )
      }
      result.missingKeys.forEach { error ->
        typedDictProblemReporter.report(
          holder,
          PyTypeCheckerSuppressionCode.BAD_TYPED_DICT,
          error.actualExpression ?: expression,
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
      val argumentType = myTypeEvalContext.getType(expression)
      val typedDictType = expectedType.typedDictType
      if (isDictExpression(expression, myTypeEvalContext)) {
        reportTypedDictProblems(typedDictType, expression)
        return
      }
      if (!match(typedDictType, argumentType, myTypeEvalContext)) {
        typedDictProblemReporter.report(
          holder,
          PyTypeCheckerSuppressionCode.BAD_ARGUMENT_TYPE,
          expression,
          PyPsiBundle.problemMessage(
            "INSP.type.checker.expected.type.got.type.instead",
            CodifiedParam.ofType(typedDictType, expression, myTypeEvalContext),
            CodifiedParam.ofType(argumentType, expression, myTypeEvalContext)
          )
        )
      }
    }

    private fun tryPromotingType(expr: PyExpression, expected: PyType?): PyType? {
      val promotedToLiteral = promoteToLiteral(expr, expected, myTypeEvalContext, null)
      if (!isUnknown(promotedToLiteral, myTypeEvalContext)) return promotedToLiteral
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
              PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.BAD_RETURN, annotationValue,
                                                  typeMismatchMessage(expected, actual, annotationValue),
                                                  effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING),
                                                  PyMakeFunctionReturnTypeQuickFix(node, myTypeEvalContext))
            }
          }
        }

        val annotatedType = myTypeEvalContext.getReturnType(node)

        if (isInitMethod(node) && !(returnsNone || annotatedType is PyNeverType)) {
          PyTypeCheckerProblemReporter.report(
            holder,
            PyTypeCheckerSuppressionCode.BAD_RETURN,
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
              PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.BAD_RETURN, annotationValue,
                                                  typeMismatchMessage(inferredType, annotatedType, annotationValue),
                                                  ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                  PyMakeFunctionReturnTypeQuickFix(node, myTypeEvalContext))
            }
          }
        }
      }
    }

    override fun visitPyNamedParameter(node: PyNamedParameter) {
      val defaultValue = flattenParens(node.defaultValue)
      if (defaultValue == null) return

      if (defaultValue is PyEllipsisLiteralExpression && (isProtocolMethodParameter(node) || isOverloadSignature(node))) {
        return
      }

      // we use `PyTypingTypeProvider.getType` of the annotation directly, instead of `node.getType`,
      //  because otherwise `PyTypingTypeProvider` will inject the type of `None`
      val expectedRef = if (hasExplicitType(node)) {
        val annotationValue = node.annotation?.value ?: return
        PyTypingTypeProvider.getType(annotationValue, myTypeEvalContext) ?: return
      }
      else {
        findInheritedParameterAnnotationType(node) ?: return
      }
      val expected = expectedRef.get()
      val actual = tryPromotingType(defaultValue, expected)

      if (actual is PySentinelType) return

      if (!matchesExpectedType(expected, actual, defaultValue, null)) {
        PyTypeCheckerProblemReporter.report(
          holder,
          PyTypeCheckerSuppressionCode.BAD_ASSIGNMENT,
          defaultValue, typeMismatchMessage(expected, actual, defaultValue),
          effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        )
      }
    }

    private fun findInheritedParameterAnnotationType(node: PyNamedParameter): Ref<PyType?>? {
      val paramName = node.name ?: return null
      val parameterList = node.parent as? PyParameterList ?: return null
      val function = parameterList.containingCallable as? PyFunction ?: return null
      if (function.containingClass == null) return null

      val superFunctions = PySuperMethodsSearch.search(function, true, myTypeEvalContext).findAll()
        .filterIsInstance<PyFunction>()

      for (superFunction in superFunctions) {
        val superParameter = superFunction.parameterList.parameters
          .filterIsInstance<PyNamedParameter>()
          .find { it.name == paramName } ?: continue
        if (!hasExplicitType(superParameter)) continue
        val annotationValue = superParameter.annotation?.value ?: continue
        val ref = PyTypingTypeProvider.getType(annotationValue, myTypeEvalContext) ?: continue
        return ref
      }
      return null
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
      if (callSite is PyCallExpression) {
        val callee = callSite.callee ?: return
        // Check constructor call self argument type
        val calleeType = myTypeEvalContext.getType(callee)
        if (calleeType is PyClassType && calleeType.isDefinition) {
          val errors = mutableListOf<PyInspectionMessages.ProblemMessage>()
          val constructorType = PyCallExpressionHelper.createCallableFromClass(calleeType, resolveContext, errors)
          if (constructorType.isUnknown) {
            for (error in errors) {
              registerProblem(callSite, error, effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
            }
            return
          }
        }

        // Calling a value of a union type is valid only if *every* member is callable and accepts the arguments
        // (a member that is not callable at all is reported by PyCallingNonCallableInspection). This differs from an
        // overloaded callable (a `PyOverloadType`, not a `PyUnionType`), for which matching *any* overload is enough.
        // TODO: intersection type
        for (component in PyCallExpressionHelper.getCalleeType(callee, resolveContext).compositeComponents) {
          val argumentsMappings = getCallableItems(component).map { mapArguments(callSite, it, myTypeEvalContext) }
          if (reportIfNoneMatches(callSite, argumentsMappings)) break
        }
      }
      else if (callSite is PyQualifiedElement) {
        val resolvedOperators = PyCallExpressionHelper.multiResolveOperatorGroupedByReceiver(callSite, resolveContext)
        val analyzedCallees = analyzeOperatorCallees(callSite, resolvedOperators)

        if (reportStrictUnionOperatorArgumentMismatch(callSite, analyzedCallees)) return
        reportIfNoCalleeMatches(callSite, analyzedCallees.orEmpty().map { it.mapping to it.calleeResults })
      }
      else {
        reportIfNoneMatches(callSite, mapArguments(callSite, resolveContext))
      }
    }

    private fun analyzeOperatorCallees(
      callSite: PyCallSiteOwner,
      callablesByMemberType: List<Pair<PyType, List<PyCallableType>>>,
    ): List<PyCalleeResults>? {
      if (callablesByMemberType.isEmpty()) return null

      val analyzedCallees = mutableListOf<PyCalleeResults>()
      for ((memberType, callables) in callablesByMemberType) {
        for (callable in callables) {
          val mapping = mapArguments(callSite, callable, myTypeEvalContext)
          if (mapping.isComplete) {
            val analysis = analyzeCallee(callSite, mapping) ?: continue
            analyzedCallees += PyCalleeResults(memberType, analysis, mapping)
          }
        }
      }

      return analyzedCallees
    }

    private fun reportStrictUnionOperatorArgumentMismatch(
      callSite: PyCallSiteOwner,
      analyzedCallees: List<PyCalleeResults>?,
    ): Boolean {
      if (!PyUnionType.isStrictSemanticsEnabled()) return false
      if (callSite !is PyQualifiedElement) return false
      if (isInsideTypeHint(callSite, myTypeEvalContext)) return false

      val operatorName = callSite.referencedName ?: return false
      val (lhs, rhs) =
        when (callSite) {
          is PyBinaryExpression ->
            callSite.leftExpression to callSite.rightExpression
          is PyAugAssignmentStatement ->
            callSite.target to callSite.value
          else -> null
        } ?: return false

      val lhsType = lhs?.let { myTypeEvalContext.getType(it) }
      val rhsType = rhs?.let { myTypeEvalContext.getType(it) }

      if (lhsType !is PyUnionType && rhsType !is PyUnionType) return false

      val leftTypes = lhsType?.strictUnionSequence()?.filterNotNull()?.filterNot { it.containsAny(context = myTypeEvalContext) }?.toList().orEmpty()
      val rightTypes = rhsType?.strictUnionSequence()?.filterNotNull()?.filterNot { it.containsAny(context = myTypeEvalContext) }?.toList().orEmpty()
      if (leftTypes.isEmpty() || rightTypes.isEmpty()) return false

      val operatorElement = callSite.nameElement?.psi ?: return false
      val operatorText = (callSite as? PyBinaryExpression)?.let { PyNames.COMPOUND_OPERATOR_DISPLAY_TEXT[it.operatorTokensText] }
                          ?: operatorElement.text

      // No operator resolved on any union member at all: there is nothing to break down, report the operands as a whole.
      if (analyzedCallees == null) {
        return PyTypeCheckerProblemReporter.report(
          holder,
          PyTypeCheckerSuppressionCode.UNSUPPORTED_OPERATOR,
          operatorElement,
          unsupportedOperatorMessage(operatorText, operatorElement, lhsType, rhsType),
          effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING),
        )
      }

      val (directOperators, reflectedOperators) = computeDispatchedOperators(callSite, operatorName)

      val directCandidates = analyzedCallees.groupByReceiver(directOperators)
      val reflectedCandidates = analyzedCallees.groupByReceiver(reflectedOperators)

      val combinationCount = leftTypes.size.toLong() * rightTypes.size.toLong()
      val skipBreakdown = combinationCount > STRICT_UNION_COMBINATION_LIMIT

      val unsupportedPairs = collectUnsupportedOperandPairs(leftTypes, rightTypes, directCandidates, reflectedCandidates,
                                                            stopAtFirstMismatch = skipBreakdown)
      if (unsupportedPairs.isEmpty()) return false

      // For large unions, stop after the first failing pair to avoid expensive, noisy breakdowns.
      if (skipBreakdown) {
        return PyTypeCheckerProblemReporter.report(
          holder,
          PyTypeCheckerSuppressionCode.UNSUPPORTED_OPERATOR,
          operatorElement,
          unsupportedOperatorMessage(operatorText, operatorElement, lhsType, rhsType),
          effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING),
        )
      }

      return reportUnsupportedOperandCombinations(operatorText, operatorElement, directCandidates, reflectedCandidates,
                                                  unsupportedPairs, directOperators, reflectedOperators)
    }

    /**
     * The (left member, right member) combinations for which neither a direct operator on the left member
     * nor a reflected one on the right member applies.
     */
    private fun collectUnsupportedOperandPairs(
      leftTypes: List<PyType>,
      rightTypes: List<PyType>,
      directCandidates: Map<PyType, List<PyCalleeResults>>,
      reflectedCandidates: Map<PyType, List<PyCalleeResults>>,
      stopAtFirstMismatch: Boolean,
    ): List<Pair<PyType, PyType>> {
      val unsupportedPairs = mutableListOf<Pair<PyType, PyType>>()

      for (leftTypeMember in leftTypes) {
        val direct = directCandidates.forReceiver(leftTypeMember)

        for (rightTypeMember in rightTypes) {
          if (acceptsOperand(direct, rightTypeMember)) continue

          val reflected = reflectedCandidates.forReceiver(rightTypeMember)
          if (acceptsOperand(reflected, leftTypeMember)) continue

          unsupportedPairs += leftTypeMember to rightTypeMember
          if (stopAtFirstMismatch) return unsupportedPairs
        }
      }

      return unsupportedPairs
    }

    private fun unsupportedOperatorMessage(
      operatorText: String,
      operatorElement: PsiElement,
      leftType: PyType?,
      rightType: PyType?,
    ): PyInspectionMessages.ProblemMessage =
      PyPsiBundle.problemMessage(
        "INSP.type.checker.unsupported.operator.between.types",
        operatorText,
        CodifiedParam.ofType(leftType, operatorElement, myTypeEvalContext),
        CodifiedParam.ofType(rightType, operatorElement, myTypeEvalContext),
      )

    /**
     * The operators dispatched on the left operand, and the reflected ones dispatched on the right operand.
     * The two sets never intersect, which is what allows telling the sides of a resolved group apart by callable name.
     */
    private fun computeDispatchedOperators(
      callSite: PyCallSiteOwner,
      operatorName: String,
    ): Pair<Set<String>, Set<String>> = when (callSite) {
      is PyBinaryExpression ->
        if (operatorName in PyNames.STANDALONE_RIGHT_OPERATORS) {
          emptySet<String>() to setOf(operatorName)
        }
        else {
          setOf(operatorName) to setOfNotNull(PyNames.leftToRightOperatorName(operatorName))
        }

      is PyAugAssignmentStatement ->
        setOfNotNull(operatorName, PyNames.inplaceToLeftOperatorName(operatorName)) to
          setOfNotNull(PyNames.inplaceToRightOperatorName(operatorName))

      else -> emptySet<String>() to emptySet()
    }

    /**
     * The already-analyzed [operatorNames] overloads grouped by receiver, merging analyses that share one:
     * a single receiver can contribute several operators, e.g. `x += y` resolves both `__iadd__`
     * and `__add__` on `x`.
     */
    private fun List<PyCalleeResults>.groupByReceiver(
      operatorNames: Set<String>,
    ): Map<PyType, List<PyCalleeResults>> {
      if (operatorNames.isEmpty()) return emptyMap()

      return filter { it.calleeResults.callable?.name in operatorNames }.groupBy({ it.memberType }, { it })
    }

    /**
     * Like [asUnionSequence], but keeps a [PyUnsafeUnionType] as a single operand so strict-union checks
     * still see it whole instead of per-member.
     */
    private fun PyType?.strictUnionSequence(): Sequence<PyType?> =
      if (this is PyUnionType) members.asSequence() else sequenceOf(this)

    private fun Map<PyType, List<PyCalleeResults>>.forReceiver(type: PyType): List<PyCalleeResults> {
      this[type]?.let { return it }
      return type.compositeComponents.filterNotNull().flatMap { this[it].orEmpty() }
    }

    private fun acceptsOperand(candidates: List<PyCalleeResults>, operandType: PyType?): Boolean {
      return candidates.any { candidate ->
        val calleeResults = candidate.calleeResults
        if (calleeResults.unmatchedArguments.isNotEmpty() ||
            calleeResults.unmatchedParameters.isNotEmpty() ||
            calleeResults.unfilledPositionalVarargs.isNotEmpty() ||
            !candidate.mapping.isComplete) {
          return@any false
        }
        val operand = calleeResults.results.singleOrNull() ?: return@any isMatched(calleeResults, candidate.mapping)
        matchesExpectedType(chooseExpectedTypeForMismatch(operand), operandType, operand.argument, null)
      }
    }

    /** Reports the [unsupportedPairs], which the caller guarantees to be non-empty, with a per-combination breakdown tooltip. */
    private fun reportUnsupportedOperandCombinations(
      operatorText: String,
      operatorElement: PsiElement,
      directCandidates: Map<PyType, List<PyCalleeResults>>,
      reflectedCandidates: Map<PyType, List<PyCalleeResults>>,
      unsupportedPairs: List<Pair<PyType, PyType>>,
      directOperators: Set<String>,
      reflectedOperators: Set<String>,
    ): Boolean {
      val leftUnion = PyUnionType.union(unsupportedPairs.mapTo(LinkedHashSet<PyType?>()) { it.first })
      val rightUnion = PyUnionType.union(unsupportedPairs.mapTo(LinkedHashSet<PyType?>()) { it.second })

      val unsupportedOperatorMessage = unsupportedOperatorMessage(operatorText, operatorElement, leftUnion, rightUnion)

      return PyTypeCheckerProblemReporter.reportWithTooltip(
        holder,
        PyTypeCheckerSuppressionCode.UNSUPPORTED_OPERATOR,
        operatorElement,
        unsupportedOperatorMessage,
        effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING),
      ) {
        PyTypeCheckerInspectionProblemRegistrar.breakdownTooltip(
          unsupportedOperatorMessage,
          explainUnsupportedOperandCombinations(
            directCandidates,
            reflectedCandidates,
            unsupportedPairs,
            leftUnion,
            rightUnion,
            directOperators,
            reflectedOperators,
            operatorElement,
          )
        )
      }
    }

    private fun explainUnsupportedOperandCombinations(
      directCandidates: Map<PyType, List<PyCalleeResults>>,
      reflectedCandidates: Map<PyType, List<PyCalleeResults>>,
      unsupportedPairs: List<Pair<PyType, PyType>>,
      leftUnionType: PyType?,
      rightUnionType: PyType?,
      directOperators: Set<String>,
      reflectedOperators: Set<String>,
      operatorElement: PsiElement,
    ): List<PyTypeMismatchExplanation> {
      val groupByRightOperand = directOperators.isEmpty() && reflectedOperators.isNotEmpty()

      val pairsByReceiver = LinkedHashMap<PyType, MutableList<Pair<PyType, PyType>>>()
      for (pair in unsupportedPairs) {
        val receiver = if (groupByRightOperand) pair.second else pair.first
        pairsByReceiver.getOrPut(receiver) { mutableListOf() } += pair
      }
      val singleReceiver = pairsByReceiver.size == 1

      return pairsByReceiver.flatMap { (receiverType, pairs) ->
        val reasons = mutableListOf<PyTypeMismatchExplanation>()
        val undefined = mutableListOf<PyType>()

        for ((leftType, rightType) in pairs) {
          val pairReasons =
            explainRejectedOperand(
              candidates = directCandidates.forReceiver(leftType),
              receiverType = leftType,
              operandType = rightType,
              operandUnionType = rightUnionType,
              anchor = operatorElement,
            ) +
            explainRejectedOperand(
              candidates = reflectedCandidates.forReceiver(rightType),
              receiverType = rightType,
              operandType = leftType,
              operandUnionType = leftUnionType,
              anchor = operatorElement,
            )
          if (pairReasons.isEmpty()) undefined += (if (groupByRightOperand) leftType else rightType) else reasons += pairReasons
        }

        val effectiveReasons = reasons.distinctBy { it.message.description }.toMutableList()
        if (undefined.isNotEmpty()) {
          val operandUnion = PyUnionType.union(LinkedHashSet<PyType?>(undefined))
          explainUndefinedOperator(
            receiverType,
            if (groupByRightOperand) receiverType else operandUnion,
            directOperators, reflectedOperators, operatorElement,
          )?.let { effectiveReasons += it }
        }

        if (singleReceiver) {
          effectiveReasons
        }
        else {
          val memberUnionType = if (groupByRightOperand) rightUnionType else leftUnionType
          val otherOperandUnion = PyUnionType.union(
            LinkedHashSet<PyType?>(pairs.map { if (groupByRightOperand) it.first else it.second })
          )
          listOf(
            PyTypeMismatchExplanation(
              PyPsiBundle.problemMessage(
                "INSP.type.checker.strict.union.unsupported.operator.member",
                CodifiedParam.ofType(receiverType, operatorElement, myTypeEvalContext),
                CodifiedParam.ofType(memberUnionType, operatorElement, myTypeEvalContext),
                operatorElement.text,
                CodifiedParam.ofType(otherOperandUnion, operatorElement, myTypeEvalContext),
              ),
              effectiveReasons,
            )
          )
        }
      }
    }

    private fun explainUndefinedOperator(
      receiverType: PyType,
      operandType: PyType?,
      directOperators: Set<String>,
      reflectedOperators: Set<String>,
      anchor: PsiElement,
    ): PyTypeMismatchExplanation? {
      val message = when {
        directOperators.isNotEmpty() && reflectedOperators.isNotEmpty() -> PyPsiBundle.problemMessage(
          "INSP.type.checker.strict.union.unsupported.operator.no.overloads",
          CodifiedParam.ofType(receiverType, anchor, myTypeEvalContext),
          CodifiedParam.joinNames(directOperators),
          CodifiedParam.ofType(operandType, anchor, myTypeEvalContext),
          CodifiedParam.joinNames(reflectedOperators),
        )
        directOperators.isNotEmpty() -> undefinedOperatorMessage(receiverType, directOperators, anchor)
        reflectedOperators.isNotEmpty() -> undefinedOperatorMessage(operandType, reflectedOperators, anchor)
        else -> return null
      }
      return PyTypeMismatchExplanation(message)
    }

    private fun undefinedOperatorMessage(
      type: PyType?,
      operatorNames: Set<String>,
      anchor: PsiElement,
    ): PyInspectionMessages.ProblemMessage =
      PyPsiBundle.problemMessage(
        "INSP.type.checker.strict.union.unsupported.operator.not.defined",
        CodifiedParam.ofType(type, anchor, myTypeEvalContext),
        CodifiedParam.joinNames(operatorNames),
      )

    private fun explainRejectedOperand(
      candidates: List<PyCalleeResults>,
      receiverType: PyType,
      operandType: PyType?,
      operandUnionType: PyType?,
      anchor: PsiElement,
    ): List<PyTypeMismatchExplanation> =
      candidates.mapNotNull { candidate ->
        if (candidate.mapping.unmappedParameters.isNotEmpty()) {
          val method = candidate.calleeResults.callable ?: return@mapNotNull null
          val methodName = method.name ?: return@mapNotNull null
          val receiverName = PythonDocumentationProvider.getTypeName(receiverType, myTypeEvalContext)
          val methodParam = CodifiedParam.ofReference(method, "$receiverName.$methodName")
          return@mapNotNull PyTypeMismatchExplanation(
            PyPsiBundle.problemMessage(
              "INSP.type.checker.strict.union.unsupported.operator.missing.argument",
              methodParam,
              CodifiedParam.joinNames(candidate.mapping.unmappedParameters.mapNotNull { it.name }),
            )
          )
        }

        val operand = candidate.calleeResults.results.singleOrNull() ?: return@mapNotNull null
        val expectedType = chooseExpectedTypeForMismatch(operand)

        if (matchesExpectedType(expectedType, operandType, operand.argument, null)) {
          return@mapNotNull null
        }

        val method = candidate.calleeResults.callable ?: return@mapNotNull null
        val methodName = method.name ?: return@mapNotNull null
        val receiverName = PythonDocumentationProvider.getTypeName(receiverType, myTypeEvalContext)
        val paramName = operand.parameter?.name

        val operandParam = CodifiedParam.ofType(operandType, anchor, myTypeEvalContext)
        val methodParam = CodifiedParam.ofReference(method, "$receiverName.$methodName")
        val expectedParam = CodifiedParam.ofType(expectedType, anchor, myTypeEvalContext, verbose = true)

        val header =
          if (operandUnionType == operandType) {
            PyPsiBundle.problemMessage(
              "INSP.type.checker.strict.union.unsupported.operator.operand.not.assignable",
              operandParam, paramName, methodParam, expectedParam,
            )
          }
          else {
            PyPsiBundle.problemMessage(
              "INSP.type.checker.strict.union.unsupported.operator.member.not.assignable",
              operandParam,
              CodifiedParam.ofType(operandUnionType, anchor, myTypeEvalContext),
              paramName, methodParam, expectedParam,
            )
          }

        val lowLevel = explainMismatch(expectedType, operandType, myTypeEvalContext, anchor)

        PyTypeMismatchExplanation(header, listOfNotNull(lowLevel))
      }

    private fun chooseExpectedTypeForMismatch(mismatch: AnalyzeArgumentResult): PyType? {
      val substituted = mismatch.expectedTypeAfterSubstitution
      val declared = mismatch.expectedType

      return if (substituted != null &&
                 substituted != declared &&
                 !substituted.containsAny(context = myTypeEvalContext)) {
        substituted
      }
      else {
        declared
      }
    }

    private fun reportIfNoneMatches(callSite: PyCallSiteOwner, argumentsMappings: List<PyArgumentsMapping>): Boolean {
      val calleesResults = argumentsMappings
        .filter { it.isComplete }
        .mapNotNull { mapping -> analyzeCallee(callSite, mapping)?.let { mapping to it } }

      return reportIfNoCalleeMatches(callSite, calleesResults)
    }

    private fun reportIfNoCalleeMatches(callSite: PyCallSiteOwner, calleesResults: List<Pair<PyArgumentsMapping, AnalyzeCalleeResults>>): Boolean {
      if (calleesResults.isNotEmpty() && calleesResults.none { (mapping, results) -> isMatched(results, mapping) }) {
        PyTypeCheckerInspectionProblemRegistrar
          .registerProblem(
            holder, callSite, calleesResults.map { it.second }, myTypeEvalContext,
            effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          )
        return true
      }
      return false
    }

    private fun checkIteratedValue(iteratedValue: PyExpression?, isAsync: Boolean): Boolean {
      return checkIteratedValue(iteratedValue, iteratedValue, isAsync)
    }

    private fun checkIteratedValue(iteratedValue: PyExpression?, highlightElement: PsiElement?, isAsync: Boolean): Boolean {
      if (iteratedValue == null || highlightElement == null) return false
      val type = myTypeEvalContext.getType(iteratedValue)
      val iterableClassName = if (isAsync) PyNames.ASYNC_ITERABLE else PyNames.ITERABLE

      if (type != null && !isUnknown(type, myTypeEvalContext) && !isSubtype(type, iterableClassName, myTypeEvalContext)) {
        val qualifiedName = "collections.$iterableClassName"
        PyTypeCheckerProblemReporter.report(
          holder,
          PyTypeCheckerSuppressionCode.NOT_ITERABLE,
          highlightElement,
          PyPsiBundle.problemMessage("INSP.type.checker.expected.type.got.type.instead", qualifiedName,
                                     CodifiedParam.ofType(type, highlightElement, myTypeEvalContext)),
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
        PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.NOT_ITERABLE, value,
                                            PyPsiBundle.problemMessage("INSP.type.checker.unpack.expected.iterable",
                                                                       CodifiedParam.ofType(type, value, myTypeEvalContext)),
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
        PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.NOT_MAPPING, value,
                                            PyPsiBundle.problemMessage("INSP.type.checker.unpack.expected.mapping",
                                                                       CodifiedParam.ofType(type, value, myTypeEvalContext)),
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
     * The declared type of [target] from its annotation. Unlike `getType(target)`, this is not narrowed by the value
     * of the current unpacking, so it is safe to compare an unpacked value against the declared annotation.
     */
    private fun resolvedDeclaredType(target: PyTargetExpression): PyType? {
      var current: PsiElement = target
      while (current is PyTargetExpression) {
        val annotationValue = current.annotation?.value
        if (annotationValue != null) {
          return Ref.deref(PyTypingTypeProvider.getType(annotationValue, myTypeEvalContext))
        }
        if (current.typeCommentAnnotation != null) return myTypeEvalContext.getType(current)
        val resolved = current.getReference(PyResolveContext.defaultContext(myTypeEvalContext)).resolve()
        if (resolved === current || resolved == null) break
        current = resolved
      }
      return null
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

    private fun checkUnpackBalance(
      targetCount: Int, starCount: Int, valueCount: Int,
      balanceHighlight: PsiElement, starHighlight: PsiElement,
      valueType: CodifiedParam,
    ): Boolean {
      if (starCount > 1) {
        PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.BAD_UNPACKING, starHighlight,
                                            PyPsiBundle.message("INSP.tuple.assignment.balance.only.one.starred.expression.allowed.in.assignment"))
        return true
      }
      // A starred target absorbs any surplus, so it only constrains the minimum number of values.
      val expectedCount = if (starCount == 1) targetCount - 1 else targetCount
      val tooMany = starCount == 0 && expectedCount < valueCount
      val notEnough = expectedCount > valueCount
      if (tooMany || notEnough) {
        val key = if (tooMany) "INSP.tuple.assignment.balance.too.many.values.to.unpack"
        else "INSP.tuple.assignment.balance.need.more.values.to.unpack"
        PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.BAD_UNPACKING, balanceHighlight,
                                            PyPsiBundle.problemMessage(key, expectedCount, valueCount, valueType),
                                            effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
        return true
      }
      return false
    }

    private fun checkNestedUnpackingBalance(targets: Array<PyExpression>, assignedTupleType: PyTupleType) {
      if (assignedTupleType.isHomogeneous) return
      val count = assignedTupleType.elementCount
      val starIndex = targets.indexOfFirst { it is PyStarExpression }
      for (i in targets.indices) {
        if (i == starIndex) continue
        val nested = flattenParens(targets[i])
        if (nested !is PyTupleExpression && nested !is PyListLiteralExpression) continue
        val effectiveIndex = if (starIndex in 0..<i) count - (targets.size - i) else i
        if (effectiveIndex !in 0..<count) continue
        val elementType = assignedTupleType.getElementType(effectiveIndex)
        if (elementType is PyTupleType) {
          checkUnpackingTargetBalance(nested, elementType)
        }
      }
    }

    private fun checkUnpackingTargetBalance(targetSeq: PySequenceExpression, assignedTupleType: PyTupleType) {
      if (assignedTupleType.isHomogeneous) return
      val valueCount = assignedTupleType.elementCount
      if (valueCount < 0) return
      val targets = targetSeq.elements
      val starCount = targets.count { it is PyStarExpression }
      val valueType = CodifiedParam.ofType(assignedTupleType, targetSeq, myTypeEvalContext)
      if (checkUnpackBalance(targets.size, starCount, valueCount, targetSeq, targetSeq, valueType)) return
      checkNestedUnpackingBalance(targets, assignedTupleType)
    }

    private fun checkContextManagerValue(iteratedValue: PyExpression?, isAsync: Boolean) {
      if (iteratedValue != null) {
        val type = myTypeEvalContext.getType(iteratedValue)
        val contextManagerClassName = if (isAsync) PyNames.ABSTRACT_ASYNC_CONTEXT_MANAGER else PyNames.ABSTRACT_CONTEXT_MANAGER

        if (type != null && !isUnknown(type, myTypeEvalContext) && !isSubtype(type, contextManagerClassName, myTypeEvalContext)) {
          val qualifiedName = "contextlib.$contextManagerClassName"
          PyTypeCheckerProblemReporter.report(
            holder,
            PyTypeCheckerSuppressionCode.BAD_CONTEXT_MANAGER,
            iteratedValue,
            PyPsiBundle.problemMessage("INSP.type.checker.expected.type.got.type.instead", qualifiedName,
                                       CodifiedParam.ofType(type, iteratedValue, myTypeEvalContext)),
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

      val substitutions = unifyReceiver(mapping, myTypeEvalContext)

      val mappedParameters = mapping.mappedParameters
      val regularMappedParameters =
        PyCallExpressionHelper.getRegularMappedParameters(mappedParameters)

      for (entry in regularMappedParameters.entries) {
        val argument: PyExpression = entry.key!!
        val parameter: PyCallableParameter = entry.value
        val expected = parameter.getArgumentType(myTypeEvalContext)
        val promotedToLiteral = promoteToLiteral(argument, expected, myTypeEvalContext, substitutions)
        val actual = promotedToLiteral.takeUnless { isUnknown(it, myTypeEvalContext) } ?: myTypeEvalContext.getType(argument)

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
          UnfilledPositionalVararg(unmappedContainer.name!!, expandedVararg)
        )
      }

      return AnalyzeCalleeResults(
        callableType, callableType.callable, result,
        unexpectedArgumentForParamSpecs,
        unfilledParameterFromParamSpecs,
        unfilledPositionalVarargs,
      )
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
          val actual = promotedToLiteral.takeUnless { isUnknown(it, myTypeEvalContext) } ?: myTypeEvalContext.getType(argument)
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
      val peeledArgument = peelArgument(argument)
      val expression = when (argument) {
        is PyStarArgument -> peelArgument(argument.childOfType<PyExpression>())
        else -> peeledArgument
      }

      if (expression != null) {
        if (isDictExpression(expression, myTypeEvalContext) && parameterType is PyTypedDictType) {
          reportTypedDictProblems(parameterType, expression)
          return true
        }
        if (parameterType is PyUnpackedTypedDictType) {
          reportUnpackedTypedDictProblems(parameterType, expression)
          return true
        }
      }

      return matchesExpectedType(parameterType, argumentType, peeledArgument, substitutions)
             && !matchingProtocolDefinitions(parameterType, argumentType, myTypeEvalContext)
    }

    private fun substituteGenerics(
      expectedArgumentType: PyType?,
      substitutions: GenericSubstitutions,
    ): PyType? {
      return if (expectedArgumentType.hasGenerics(myTypeEvalContext))
        substitute(expectedArgumentType, substitutions, myTypeEvalContext)
      else
        PyAnyType.unknown
    }

    companion object {
      fun getExpectedReturnStatementType(function: PyFunction, typeEvalContext: TypeEvalContext): PyType? {
        val returnType = typeEvalContext.getReturnType(function)
        if (function.isGenerator) {
          val generatorDesc = fromGeneratorOrProtocol(returnType, typeEvalContext)
          if (generatorDesc != null) {
            return generatorDesc.returnType
          }
          return PyAnyType.unknown
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
        return type is PyClassType &&
               type.typeArguments.any { requiresTypeSpecialization(it) }
      }

      private fun getParamSpecSubstitution(
        paramSpecType: PyParamSpecType,
        substitutions: GenericSubstitutions,
      ): PyCallableParameterListType? {
        return substitutions.paramSpecs[paramSpecType] as? PyCallableParameterListType
      }

      private fun isMatched(calleeResults: AnalyzeCalleeResults, mapping: PyArgumentsMapping): Boolean {
        return calleeResults.results.all { it.isMatched } &&
               calleeResults.unmatchedArguments.isEmpty() &&
               calleeResults.unmatchedParameters.isEmpty() &&
               calleeResults.unfilledPositionalVarargs.isEmpty() &&
               mapping.isComplete
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

  internal class TypedDictProblemReporter {
    private val reportedKeys = mutableSetOf<Pair<PsiElement, String>>()

    fun report(
      holder: ProblemsHolder,
      code: PyTypeCheckerSuppressionCode,
      element: PsiElement,
      message: PyInspectionMessages.ProblemMessage,
      type: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
    ) {
      if (reportedKeys.add(element to message.description)) {
        PyTypeCheckerProblemReporter.report(holder, code, element, message, type)
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

  internal class PyCalleeResults(
    val memberType: PyType,
    val calleeResults: AnalyzeCalleeResults,
    val mapping: PyArgumentsMapping
  )

  internal class AnalyzeArgumentResult(
    val argument: PyExpression,
    val parameter: PyCallableParameter?,
    val expectedType: PyType?,
    val expectedTypeAfterSubstitution: PyType?,
    val actualType: PyType?,
    val isMatched: Boolean,
  ) {
    init {
      PyAnyType.validate(expectedTypeAfterSubstitution)
    }
  }

  internal class UnfilledParameterFromParamSpec(val parameter: PyCallableParameter, val paramSpecType: PyParamSpecType)

  internal class UnexpectedArgumentForParamSpec(val argument: PyExpression, val paramSpecType: PyParamSpecType)

  @JvmRecord
  internal data class UnfilledPositionalVararg(@JvmField val varargName: String, @JvmField val expectedType: PyType?)
  companion object {
    private val LOG = thisLogger()
    private val TIME_KEY = Key.create<Long>("PyTypeCheckerInspection.StartTime")

    private const val STRICT_UNION_COMBINATION_LIMIT: Int = 16
  }
}
