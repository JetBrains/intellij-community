package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyFunction

class PyAssertTypeInspection : PyInspection() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return object : PyInspectionVisitor(holder, getContext(session)) {
      override fun visitPyCallExpression(callExpression: PyCallExpression) {
        val callable = callExpression.multiResolveCalleeFunction(resolveContext).singleOrNull()
        if (callable is PyFunction && PyTypingTypeProvider.ASSERT_TYPE == callable.qualifiedName) {
          val arguments = callExpression.getArguments()
          if (arguments.size == 2) {
            val actualType = myTypeEvalContext.getType(arguments[0])
            val expectedType = Ref.deref(PyTypingTypeProvider.getType(arguments[1], myTypeEvalContext))
            if (actualType != expectedType) {
              val expectedName = PythonDocumentationProvider.getVerboseTypeName(expectedType, myTypeEvalContext)
              val actualName = PythonDocumentationProvider.getTypeName(actualType, myTypeEvalContext)
              registerProblem(arguments[0],
                              PyPsiBundle.message("INSP.assert.type.expected.type.got.type.instead", expectedName, actualName))
            }
          }
        }
      }
    }
  }
}