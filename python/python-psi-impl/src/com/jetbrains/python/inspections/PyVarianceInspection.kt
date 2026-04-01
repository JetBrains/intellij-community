package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.parentOfType
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.codeInsight.typing.isProtocol
import com.jetbrains.python.psi.PyAnnotation
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.types.PyExpectedVarianceJudgment
import com.jetbrains.python.psi.types.PyInferredVarianceJudgment
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeVarType
import com.jetbrains.python.psi.types.PyTypeVarType.Variance
import com.jetbrains.python.psi.types.PyTypeVarType.Variance.BIVARIANT
import com.jetbrains.python.psi.types.PyTypeVarType.Variance.CONTRAVARIANT
import com.jetbrains.python.psi.types.PyTypeVarType.Variance.COVARIANT
import com.jetbrains.python.psi.types.PyTypeVarType.Variance.INFER_VARIANCE
import com.jetbrains.python.psi.types.PyTypeVarType.Variance.INVARIANT
import com.jetbrains.python.psi.types.TypeEvalContext


class PyVarianceInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    val context = PyInspectionVisitor.getContext(session)
    val typeExpressionVisitor = TypeExpressionVisitor(holder, context)
    return PotentialLocationsVisitor(typeExpressionVisitor, holder, PyInspectionVisitor.getContext(session))
  }

  private class PotentialLocationsVisitor(
    val typeExpressionVisitor: TypeExpressionVisitor,
    holder: ProblemsHolder,
    val context: TypeEvalContext,
  ) : PyInspectionVisitor(holder, context) {

    // Inspection: "Obsolete variance for constrained TypeVar"
    override fun visitPyAssignmentStatement(node: PyAssignmentStatement) {
      super.visitPyAssignmentStatement(node)
      val typeVarCall = node.assignedValue as? PyCallExpression ?: return
      val callee = PyCallExpressionHelper.resolveCalleeClass(typeVarCall) ?: return
      if (PyTypingTypeProvider.TYPE_VAR != callee.qualifiedName) return
      val typeVarType = PyTypingTypeProvider.getType(typeVarCall, context)?.get() as? PyTypeVarType ?: return
      if (typeVarType.constraints.size < 2) return
      if (typeVarType.constraints.size > 7) return // escape hatch wrt. performance
      if (typeVarType.variance != COVARIANT && typeVarType.variance != CONTRAVARIANT) return
      val argList = typeVarCall.argumentList ?: return
      val varianceArg = argList.arguments.firstOrNull { arg -> arg.name == "covariant" || arg.name == "contravariant" } ?: return
      // now check that at least two constraints have a subtype relation
      for ((idx1, c1) in typeVarType.constraints.withIndex()) {
        for (idx2 in idx1 + 1 until typeVarType.constraints.size) {
          val c2 = typeVarType.constraints[idx2]
          if (PyTypeChecker.match(c1, c2, context)) return
          if (PyTypeChecker.match(c2, c1, context)) return
        }
      }
      // no two constraints have a subtype relation
      val msg = PyPsiBundle.message("INSP.variance.checker.superfluous")
      holder!!.registerProblem(varianceArg, msg, ProblemHighlightType.LIKE_UNUSED_SYMBOL)
    }

    // Below for inspection: "Incompatible variance"

    // Class attribute type annotation
    override fun visitPyTargetExpression(node: PyTargetExpression) {
      super.visitPyTargetExpression(node)
      if (node.containingClass == null) return
      if (node.isQualified) return
      val annotation = node.annotation ?: return
      annotation.accept(typeExpressionVisitor)
    }

    // Parameter type annotation
    override fun visitPyNamedParameter(node: PyNamedParameter) {
      super.visitPyNamedParameter(node)
      val annotation = node.annotation ?: return
      annotation.accept(typeExpressionVisitor)
    }

    // Return type annotation
    override fun visitPyFunction(node: PyFunction) {
      super.visitPyFunction(node)
      val annotation = node.annotation ?: return
      annotation.accept(typeExpressionVisitor)
    }

    // Superclasses (bases)
    override fun visitPyClass(node: PyClass) {
      super.visitPyClass(node)
      for (superClassExpression in node.superClassExpressions) {
        superClassExpression.accept(typeExpressionVisitor)
      }
    }
  }

  private inner class TypeExpressionVisitor(val holder: ProblemsHolder, val context: TypeEvalContext) : PyElementVisitor() {
    override fun visitPyAnnotation(node: PyAnnotation) {
      node.acceptChildren(this)
    }

    override fun visitPyExpression(node: PyExpression) {
      node.acceptChildren(this)
    }

    override fun visitPyStringLiteralExpression(node: PyStringLiteralExpression) {
      val syntheticElement = PyUtil.createExpressionFromFragment(node.stringValue, node) ?: return
      syntheticElement.acceptChildren(this)
    }

    override fun visitPyReferenceExpression(node: PyReferenceExpression) {
      val type = PyTypingTypeProvider.getType(node, context)?.get() ?: return
      if (type !is PyTypeVarType) return
      onPyTypeValTypeUsedInAnnotation(holder, node, context)
    }
  }


  private fun onPyTypeValTypeUsedInAnnotation(holder: ProblemsHolder, node: PyReferenceExpression, context: TypeEvalContext) {
    val isInProtocol = node.parentOfType<PyClass>()?.isProtocol(context) ?: false
    val subscriptionExpression = node.parentOfType<PySubscriptionExpression>()
    val isInProtocolHeader = subscriptionExpression?.parent is PyArgumentList && subscriptionExpression.parent?.parent is PyClass
    if (isInProtocol && isInProtocolHeader) {
      checkProtocol(holder, node, context)
    }
    else {
      checkClass(holder, node, context)
    }
  }

  private fun checkProtocol(holder: ProblemsHolder, node: PyReferenceExpression, context: TypeEvalContext) {
    val varianceExpected = PyInferredVarianceJudgment.getInferredVariance(node, context) ?: return
    val varianceActual = PyInferredVarianceJudgment.getDeclaredOrInferredVariance(node, context) ?: return

    val isInProtocolSubscriptionExpr = isInProtocolSubscriptionExpr(node)
    if (isInProtocolSubscriptionExpr) {
      val varianceExpected = if (varianceExpected == BIVARIANT) COVARIANT else varianceExpected
      if (varianceExpected != varianceActual) {
        val physicalNode = PyUtil.getFragmentContext(node) ?: return
        val msg = PyPsiBundle.message("INSP.variance.checker.protocols.keep.effective.variance",
                                      varianceExpected.name.lowercase(), varianceActual.name.lowercase())
        holder.registerProblem(physicalNode, msg)
      }
    }
    else checkIncompatibleVariance(varianceActual, varianceExpected, holder, node)
  }

  private fun isInProtocolSubscriptionExpr(node: PyReferenceExpression): Boolean {
    val parentSubscriptionExpr = node.parent as? PySubscriptionExpression
                                 ?: (node.parent as? PyTupleExpression)?.parent as? PySubscriptionExpression
                                 ?: return false
    if (node == parentSubscriptionExpr.indexExpression) return true
    val indexTupleExpression = parentSubscriptionExpr.indexExpression as? PyTupleExpression ?: return false
    val nodeTupleParent = node.parentOfType<PyTupleExpression>()
    return indexTupleExpression === nodeTupleParent
  }

  private fun checkClass(holder: ProblemsHolder, node: PyReferenceExpression, context: TypeEvalContext) {
    val varianceExpected = PyExpectedVarianceJudgment.getExpectedVariance(node, context) ?: return
    if (varianceExpected == BIVARIANT) return
    val varianceInferred = PyInferredVarianceJudgment.getDeclaredOrInferredVariance(node, context) ?: return

    checkIncompatibleVariance(varianceInferred, varianceExpected, holder, node)
  }

  private fun checkIncompatibleVariance(
    varianceInferred: Variance,
    varianceExpected: Variance,
    holder: ProblemsHolder,
    node: PyReferenceExpression,
  ) {
    if (isCompatibleWith(varianceInferred, varianceExpected)) return

    val physicalNode = PyUtil.getFragmentContext(node) ?: return
    val msg = PyPsiBundle.message("INSP.variance.checker.incompatible",
                                  varianceExpected.name.lowercase(), varianceInferred.name.lowercase())
    holder.registerProblem(physicalNode, msg)
  }

  /**
   * Returns true iff declared/actual variance is compatible with the required/expected variance.
   *
   * Compatibility rules (typical for variance checking):
   * - INFER_VARIANCE is treated as "unknown / don't care" and is compatible with anything.
   * - INVARIANT can be used in both co- and contravariant positions (but not vice versa).
   * - COVARIANT is only compatible with a covariant position.
   * - CONTRAVARIANT is only compatible with a contravariant position.
   */
  fun isCompatibleWith(actual: Variance, expected: Variance): Boolean {
    if (actual == INFER_VARIANCE || expected == INFER_VARIANCE) return true

    return when (expected) {
      COVARIANT -> actual == COVARIANT || actual == INVARIANT
      CONTRAVARIANT -> actual == CONTRAVARIANT || actual == INVARIANT
      INVARIANT -> actual == INVARIANT
      BIVARIANT -> true
    }
  }
}
