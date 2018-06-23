// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyABCUtil
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.refactoring.PyReplaceExpressionUtil


class PyAsyncCallInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return Visitor(holder, session)
  }

  private class Visitor(holder: ProblemsHolder, session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {

    override fun visitPyExpressionStatement(node: PyExpressionStatement) {
      val expr = node.expression
      if (expr is PyCallExpression && isAwaitableCall(expr)) {
        val awaitableType = when {
          isOuterFunctionAsync(expr) -> AwaitableType.AWAITABLE
          isOuterFunctionCoroutine(expr, myTypeEvalContext) -> AwaitableType.COROUTINE
          else -> return
        }
        // no need to check whether await or yield from is missed, because it's PyCallExpression already, not PyPrefixExpression
        val functionName = getCalledCoroutineName(expr, resolveContext) ?: return
        registerProblem(node, PyBundle.message("INSP.NAME.coroutine.is.not.awaited", functionName),
                        PyAddAwaitCallForCoroutineFix(awaitableType))
      }
    }

    private fun isAwaitableCall(callExpr: PyCallExpression): Boolean {
      val type = myTypeEvalContext.getType(callExpr) ?: return false
      return PyABCUtil.isSubtype(type, PyNames.AWAITABLE, myTypeEvalContext) ||
             type is PyClassType &&
             PyTypingTypeProvider.GENERATOR == type.classQName &&
             PyKnownDecoratorUtil.isResolvedToGeneratorBasedCoroutine(callExpr, resolveContext, myTypeEvalContext)
    }
  }

  companion object {
    enum class AwaitableType {
      AWAITABLE, COROUTINE
    }

    const val coroutineIsNotAwaited = "Coroutine is not awaited"

    private fun getCalledCoroutineName(callExpression: PyCallExpression, resolveContext: PyResolveContext): String? {
      val callee = callExpression.callee as? PyReferenceExpression ?: return null
      val function = callExpression.multiResolveCalleeFunction(resolveContext).firstOrNull() as? PyFunction ?: return null
      return if (function.name == PyNames.INIT) callee.name else function.name
    }

    fun isOuterFunctionAsync(node: PyExpression): Boolean {
      return (ScopeUtil.getScopeOwner(node) as? PyFunction)?.isAsync ?: false
    }

    private fun isOuterFunctionCoroutine(node: PyExpression, typeEvalContext: TypeEvalContext): Boolean {
      val pyFunction = (ScopeUtil.getScopeOwner(node) as? PyFunction) ?: return false
      return PyKnownDecoratorUtil.hasGeneratorBasedCoroutineDecorator(pyFunction, typeEvalContext)
    }
  }

  private class PyAddAwaitCallForCoroutineFix(val type: AwaitableType) : LocalQuickFix {
    override fun getFamilyName() = coroutineIsNotAwaited

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val psiElement = descriptor.psiElement
      if (psiElement is PyExpressionStatement) {
        val sb = StringBuilder()
        when (type) {
          AwaitableType.AWAITABLE -> {
            sb.append("""async def foo():
                         |    """.trimMargin()).append(PyNames.AWAIT).append(" ").append(psiElement.text)
          }
          AwaitableType.COROUTINE -> {
            sb.append("""def foo():
                         |    """.trimMargin()).append(PyNames.YIELD).append(" ").append(PyNames.FROM).append(" ")
              .append(psiElement.text)
          }
        }
        val generator = PyElementGenerator.getInstance(project)
        val function = generator.createFromText(LanguageLevel.forElement(psiElement), PyFunction::class.java, sb.toString())
        val awaitedStatement = function.statementList.statements.firstOrNull()
        if (awaitedStatement != null) {
          PyReplaceExpressionUtil.replaceExpression(psiElement, awaitedStatement)
        }
      }
    }
  }
}