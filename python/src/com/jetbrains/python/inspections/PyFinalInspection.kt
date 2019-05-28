// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.functionTypeComments.psi.PyParameterTypeList
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.getFunctionTypeAnnotation
import com.jetbrains.python.documentation.doctest.PyDocstringFile
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.PyKnownDecoratorUtil.KnownDecorator.TYPING_FINAL
import com.jetbrains.python.psi.PyKnownDecoratorUtil.KnownDecorator.TYPING_FINAL_EXT
import com.jetbrains.python.psi.impl.PyClassImpl
import com.jetbrains.python.psi.search.PySuperMethodsSearch
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.pyi.PyiUtil

class PyFinalInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = Visitor(holder, session)

  private class Visitor(holder: ProblemsHolder, session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {

    override fun visitPyClass(node: PyClass) {
      super.visitPyClass(node)

      node.superClassExpressions.forEach {
        val cls = (myTypeEvalContext.getType(it) as? PyClassType)?.pyClass
        if (cls != null && isFinal(cls)) {
          registerProblem(it, "'${cls.name}' is marked as '@final' and should not be subclassed")
        }
      }

      if (PyiUtil.isInsideStub(node)) {
        val visitedNames = mutableSetOf<String?>()

        node.visitMethods(
          { m ->
            if (!visitedNames.add(m.name) && isFinal(m)) {
              registerProblem(m.nameIdentifier, "'@final' should be placed on the first overload")
            }
            true
          },
          false,
          myTypeEvalContext
        )
      }
      else {
        checkClassLevelFinalsAreInitialized(node)
      }

      checkRedeclarationsInScope(node)
    }

    override fun visitPyFunction(node: PyFunction) {
      super.visitPyFunction(node)

      if (node.containingClass != null) {
        PySuperMethodsSearch
          .search(node, myTypeEvalContext)
          .firstOrNull { it is PyFunction && isFinal(it) }
          ?.let {
            registerProblem(node.nameIdentifier, "'${(it as PyFunction).qualifiedName}' is marked as '@final' and should not be overridden")
          }

        if (!PyiUtil.isInsideStub(node)) {
          if (isFinal(node) && PyiUtil.isOverload(node, myTypeEvalContext)) {
            registerProblem(node.nameIdentifier, "'@final' should be placed on the implementation")
          }

          checkInstanceFinalsOutsideInit(node)
        }
      }
      else if (isFinal(node)) {
        registerProblem(node.nameIdentifier, "Non-method function could not be marked as '@final'")
      }

      getFunctionTypeAnnotation(node)?.let { comment ->
        if (comment.parameterTypeList.parameterTypes.any { resolvesToFinal(if (it is PySubscriptionExpression) it.operand else it) }) {
          registerProblem(node.typeComment, "'Final' could not be used in annotations for function parameters")
        }
      }

      checkRedeclarationsInScope(node)
    }

    override fun visitPyTargetExpression(node: PyTargetExpression) {
      super.visitPyTargetExpression(node)

      if (!node.hasAssignedValue()) {
        node.annotation?.value?.let {
          if (PyiUtil.isInsideStub(node) || ScopeUtil.getScopeOwner(node) is PyClass) {
            if (resolvesToFinal(it)) {
              registerProblem(it, "If assigned value is omitted, there should be an explicit type argument to 'Final'")
            }
          }
          else {
            if (resolvesToFinal(if (it is PySubscriptionExpression) it.operand else it)) {
              registerProblem(node, "'Final' name should be initialized with a value")
            }
          }
        }
      }
    }

    override fun visitPyNamedParameter(node: PyNamedParameter) {
      super.visitPyNamedParameter(node)

      if (isFinal(node)) {
        registerProblem(node.annotation?.value ?: node.typeComment, "'Final' could not be used in annotations for function parameters")
      }
    }

    override fun visitPyReferenceExpression(node: PyReferenceExpression) {
      super.visitPyReferenceExpression(node)

      checkFinalIsOuterMost(node)
    }

    override fun visitPyFile(node: PyFile) {
      super.visitPyFile(node)

      checkRedeclarationsInScope(node)
    }

    private fun checkRedeclarationsInScope(scopeOwner: ScopeOwner) {
      val visitedNames = mutableSetOf<String?>()
      ControlFlowCache.getScope(scopeOwner).targetExpressions.forEach {
        if (!visitedNames.add(it.name) && isFinal(it)) {
          registerProblem(it, "Already declared name could not be redefined as 'Final'")
        }
      }
    }

    private fun checkClassLevelFinalsAreInitialized(cls: PyClass) {
      val notInitializedFinals = mutableMapOf<String?, PyTargetExpression>()

      cls.classAttributes.forEach {
        if (!it.hasAssignedValue() && isFinal(it)) {
          notInitializedFinals[it.name] = it
        }
      }

      if (notInitializedFinals.isNotEmpty()) {
        cls.findMethodByName(PyNames.INIT, false, myTypeEvalContext)?.let {
          val initializedAttributes = mutableMapOf<String, PyTargetExpression>()
          PyClassImpl.collectInstanceAttributes(it, initializedAttributes)
          notInitializedFinals -= initializedAttributes.keys
        }

        notInitializedFinals.values.forEach {
          registerProblem(it, "'Final' name should be initialized with a value")
        }
      }
    }

    private fun checkInstanceFinalsOutsideInit(method: PyFunction) {
      if (PyUtil.isInit(method)) return

      val instanceAttributes = mutableMapOf<String, PyTargetExpression>()
      PyClassImpl.collectInstanceAttributes(method, instanceAttributes)
      instanceAttributes.values.forEach {
        if (isFinal(it)) registerProblem(it, "'Final' attribute should be declared in class body or '__init__'")
      }
    }

    private fun checkFinalIsOuterMost(node: PyReferenceExpression) {
      if (isTopLevelInAnnotationOrTypeComment(node)) return
      (node.parent as? PySubscriptionExpression)?.let {
        if (it.operand == node && isTopLevelInAnnotationOrTypeComment(it)) return
      }

      if (PyTypingTypeProvider.isInAnnotationOrTypeComment(node) && resolvesToFinal(node)) {
        registerProblem(node, "'Final' could only be used as the outermost type")
      }
    }

    private fun isFinal(decoratable: PyDecoratable): Boolean {
      return PyKnownDecoratorUtil.getKnownDecorators(decoratable, myTypeEvalContext).any { it == TYPING_FINAL || it == TYPING_FINAL_EXT }
    }

    private fun <T> isFinal(node: T): Boolean where T : PyAnnotationOwner, T : PyTypeCommentOwner {
      val typeHint = typeHintAsExpression(node)
      return resolvesToFinal(if (typeHint is PySubscriptionExpression) typeHint.operand else typeHint)
    }

    private fun isFinal(qualifiedName: String?): Boolean {
      return qualifiedName == PyTypingTypeProvider.FINAL || qualifiedName == PyTypingTypeProvider.FINAL_EXT
    }

    private fun resolvesToFinal(expression: PyExpression?): Boolean {
      return expression is PyReferenceExpression &&
             expression
               .multiFollowAssignmentsChain(resolveContext) { !isFinal(it.qualifiedName) }
               .asSequence()
               .mapNotNull { it.element }
               .any { it is PyTargetExpression && isFinal(it.qualifiedName) }
    }

    private fun <T> typeHintAsExpression(node: T): PyExpression? where T : PyAnnotationOwner, T : PyTypeCommentOwner {
      val annotation = node.annotation?.value
      if (annotation != null) return annotation

      val typeComment = node.typeCommentAnnotation
      if (typeComment == null) return null

      val file = FileContextUtil.getContextFile(node) ?: return null
      return PyUtil.createExpressionFromFragment(typeComment, file)
    }

    private fun isTopLevelInAnnotationOrTypeComment(node: PyExpression): Boolean {
      if (node.parent is PyAnnotation) return true
      if (node.parent is PyExpressionStatement && node.parent.parent is PyDocstringFile) return true
      if (node.parent is PyParameterTypeList) return true
      return false
    }
  }
}