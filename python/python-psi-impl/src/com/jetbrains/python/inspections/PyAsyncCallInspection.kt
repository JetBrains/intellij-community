// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyABCUtil
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.refactoring.PyReplaceExpressionUtil


class PyAsyncCallInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return Visitor(holder, PyInspectionVisitor.getContext(session))
  }

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyExpressionStatement(node: PyExpressionStatement) {
      val expr = node.expression
      if (expr is PyCallExpression && isAwaitableCall(expr) && !isIgnored(expr)) {
        val awaitableType = when {
          isOuterFunctionAsync(expr) -> AwaitableType.AWAITABLE
          isOuterFunctionCoroutine(expr, myTypeEvalContext) -> AwaitableType.COROUTINE
          else -> return
        }
        // no need to check whether await or yield from is missed, because it's PyCallExpression already, not PyPrefixExpression
        val functionName = getCalledCoroutineName(expr, resolveContext) ?: return
        registerProblem(node, PyPsiBundle.message("INSP.NAME.coroutine.is.not.awaited", functionName),
                        PyAddAwaitCallForCoroutineFix(awaitableType))
      }
    }

    private fun isIgnored(callExpr: PyCallExpression): Boolean {
      // ignore functions which return Task
      val type = myTypeEvalContext.getType(callExpr) ?: return true
      val typeQName = if (type is PyClassLikeType) type.classQName else null
      if (ignoreReturnedType.contains(typeQName)) return true

      // ignore builtin functions which schedule function to run in a loop without `await`
      val qualifiedName = callExpr.multiResolveCalleeFunction(resolveContext).firstOrNull()?.qualifiedName
      return ignoreBuiltinFunctions.contains(qualifiedName)
    }

    private fun isAwaitableCall(callExpr: PyCallExpression): Boolean {
      val type = myTypeEvalContext.getType(callExpr) ?: return false
      return PyABCUtil.isSubtype(type, PyNames.AWAITABLE, myTypeEvalContext) ||
             PyTypingTypeProvider.isGenerator(type) &&
             PyKnownDecoratorUtil.isResolvedToGeneratorBasedCoroutine(callExpr, resolveContext, myTypeEvalContext)
    }
  }

  companion object {
    enum class AwaitableType {
      AWAITABLE, COROUTINE
    }

    val ignoreReturnedType = listOf("asyncio.tasks.Task")
    val ignoreBuiltinFunctions = listOf("asyncio.events.AbstractEventLoop.run_in_executor",
                                        "asyncio.tasks.ensure_future",
                                        "asyncio.ensure_future")

    private fun getCalledCoroutineName(callExpression: PyCallExpression, resolveContext: PyResolveContext): String? {
      val callee = callExpression.callee as? PyReferenceExpression ?: return null
      val function = callExpression.multiResolveCalleeFunction(resolveContext).firstOrNull() as? PyFunction ?: return null
      return if (PyUtil.isInitMethod(function)) callee.name else function.name
    }

    fun isOuterFunctionAsync(node: PyExpression): Boolean {
      return (ScopeUtil.getScopeOwner(node) as? PyFunction)?.isAsync ?: false
    }

    private fun isOuterFunctionCoroutine(node: PyExpression, typeEvalContext: TypeEvalContext): Boolean {
      val pyFunction = (ScopeUtil.getScopeOwner(node) as? PyFunction) ?: return false
      return PyKnownDecoratorUtil.hasGeneratorBasedCoroutineDecorator(pyFunction, typeEvalContext)
    }
  }

  private class PyAddAwaitCallForCoroutineFix(val type: AwaitableType) : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName() = PyPsiBundle.message("QFIX.coroutine.is.not.awaited")

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
      if (element is PyExpressionStatement) {
        val sb = StringBuilder()
        when (type) {
          AwaitableType.AWAITABLE -> {
            sb.append("""async def foo():
                         |    """.trimMargin()).append(PyNames.AWAIT).append(" ").append(element.text)
          }
          AwaitableType.COROUTINE -> {
            sb.append("""def foo():
                         |    """.trimMargin()).append(PyNames.YIELD).append(" ").append(PyNames.FROM).append(" ")
              .append(element.text)
          }
        }
        val generator = PyElementGenerator.getInstance(project)
        val function = generator.createFromText(LanguageLevel.forElement(element), PyFunction::class.java, sb.toString())
        val awaitedStatement = function.statementList.statements.firstOrNull()
        if (awaitedStatement != null) {
          PyReplaceExpressionUtil.replaceExpression(element, awaitedStatement)
        }
      }
    }
  }
}
