 package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.quickfix.PyMakeReturnsExplicitFix
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyReturnStatement

/**
 * PEP8:
 * Be consistent in return statements. Either all return statements in a function should return an expression,
 * or none of them should. If any return statement returns an expression, any return statements where no value
 * is returned should explicitly state this as return None, and an explicit return statement should be present
 * at the end of the function (if reachable).
 * 
 * @see PyMakeReturnsExplicitFix for tests
 */
class PyInconsistentReturnsInspection : PyInspection() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return object : PyInspectionVisitor(holder, getContext(session)) {
      override fun visitPyFunction(node: PyFunction) {
        val returnPoints = node.getReturnPoints(myTypeEvalContext)
        val hasExplicitReturns = returnPoints.any { (it as? PyReturnStatement)?.expression != null }
        if (hasExplicitReturns) {
          returnPoints
            .filter { (it !is PyReturnStatement || it.expression == null) }
            .forEach {
              val message =
                if (it is PyReturnStatement) PyPsiBundle.message("INSP.inconsistent.returns.value.expected")
                else PyPsiBundle.message("INSP.inconsistent.returns.stmt.expected")
              this.holder!!.problem(it, message).fix(PyMakeReturnsExplicitFix(node)).register()
            }
        }
      }
    }
  }
}