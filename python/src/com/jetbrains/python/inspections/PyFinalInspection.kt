// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.functionTypeComments.psi.PyFunctionTypeAnnotation
import com.jetbrains.python.codeInsight.functionTypeComments.psi.PyParameterTypeList
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.*
import com.jetbrains.python.documentation.doctest.PyDocstringFile
import com.jetbrains.python.psi.*
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

      node.getSuperClasses(myTypeEvalContext).filter { isFinal(it) }.let { finalSuperClasses ->
        if (finalSuperClasses.isEmpty()) return@let

        val postfix = " ${if (finalSuperClasses.size == 1) "is" else "are"} marked as '@final' and should not be subclassed"
        registerProblem(node.nameIdentifier, finalSuperClasses.joinToString(postfix = postfix) { "'${it.name}'" ?: "" })
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
        val (classLevelFinals, initAttributes) = getClassLevelFinalsAndInitAttributes(node)
        checkClassLevelFinalsAreInitialized(classLevelFinals, initAttributes)
        checkSameNameClassAndInstanceFinals(classLevelFinals, initAttributes)
      }

      checkOverridingInheritedFinalWithNewOne(node)
    }

    override fun visitPyFunction(node: PyFunction) {
      super.visitPyFunction(node)

      val cls = node.containingClass
      if (cls != null) {
        PySuperMethodsSearch
          .search(node, myTypeEvalContext)
          .asSequence()
          .filterIsInstance<PyFunction>()
          .firstOrNull { isFinal(it) }
          ?.let {
            val qualifiedName = it.qualifiedName ?: it.containingClass?.name + "." + it.name
            registerProblem(node.nameIdentifier, "'$qualifiedName' is marked as '@final' and should not be overridden")
          }

        if (!PyiUtil.isInsideStub(node)) {
          if (isFinal(node) && PyiUtil.isOverload(node, myTypeEvalContext)) {
            registerProblem(node.nameIdentifier, "'@final' should be placed on the implementation")
          }

          checkInstanceFinalsOutsideInit(node)
        }

        if (PyKnownDecoratorUtil.hasAbstractDecorator(node, myTypeEvalContext)) {
          if (isFinal(node)) {
            registerProblem(node.nameIdentifier, "'Final' could not be mixed with abstract decorators")
          }
          else if (isFinal(cls)) {
            val message = "'Final' class could not contain abstract methods"
            registerProblem(node.nameIdentifier, message)
            registerProblem(cls.nameIdentifier, message)
          }
        }
        else if (isFinal(node) && isFinal(cls)) {
          registerProblem(node.nameIdentifier, "No need to mark method in 'Final' class as '@final'", ProblemHighlightType.WEAK_WARNING)
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

      getReturnTypeAnnotation(node, myTypeEvalContext)?.let {
        if (resolvesToFinal(if (it is PySubscriptionExpression) it.operand else it)) {
          registerProblem(node.typeComment ?: node.annotation, "'Final' could not be used in annotation for function return value")
        }
      }
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
      else if (!isFinal(node)) {
        checkFinalReassignment(node)
      }

      if (isFinal(node) && PyUtil.multiResolveTopPriority(node, resolveContext).any { it != node }) {
        registerProblem(node, "Already declared name could not be redefined as 'Final'")
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

    override fun visitPyForStatement(node: PyForStatement) {
      super.visitPyForStatement(node)
      checkFinalInsideLoop(node)
    }

    override fun visitPyWhileStatement(node: PyWhileStatement) {
      super.visitPyWhileStatement(node)
      checkFinalInsideLoop(node)
    }

    override fun visitPyAugAssignmentStatement(node: PyAugAssignmentStatement) {
      super.visitPyAugAssignmentStatement(node)

      val target = node.target
      if (target is PyQualifiedExpression) {
        checkFinalReassignment(target)
      }
    }

    private fun getClassLevelFinalsAndInitAttributes(cls: PyClass): Pair<Map<String?, PyTargetExpression>, Map<String, PyTargetExpression>> {
      val classLevelFinals = mutableMapOf<String?, PyTargetExpression>()
      cls.classAttributes.forEach { if (isFinal(it)) classLevelFinals[it.name] = it }

      val initAttributes = mutableMapOf<String, PyTargetExpression>()
      cls.findMethodByName(PyNames.INIT, false, myTypeEvalContext)?.let { PyClassImpl.collectInstanceAttributes(it, initAttributes) }

      return Pair(classLevelFinals, initAttributes)
    }

    private fun getDeclaredClassAndInstanceFinals(cls: PyClass): Pair<Map<String, PyTargetExpression>, Map<String, PyTargetExpression>> {
      val classFinals = mutableMapOf<String, PyTargetExpression>()
      val instanceFinals = mutableMapOf<String, PyTargetExpression>()

      for (classAttribute in cls.classAttributes) {
        val name = classAttribute.name ?: continue

        if (isFinal(classAttribute)) {
          val mapToPut = if (classAttribute.hasAssignedValue()) classFinals else instanceFinals
          mapToPut[name] = classAttribute
        }
      }

      cls.findMethodByName(PyNames.INIT, false, myTypeEvalContext)?.let { init ->
        val attributesInInit = mutableMapOf<String, PyTargetExpression>()
        PyClassImpl.collectInstanceAttributes(init, attributesInInit, instanceFinals.keys)
        instanceFinals += attributesInInit.filterValues { isFinal(it) }
      }

      return Pair(classFinals, instanceFinals)
    }

    private fun checkClassLevelFinalsAreInitialized(classLevelFinals: Map<String?, PyTargetExpression>,
                                                    initAttributes: Map<String, PyTargetExpression>) {
      classLevelFinals.forEach { (name, psi) ->
        if (!psi.hasAssignedValue() && name !in initAttributes) {
          registerProblem(psi, "'Final' name should be initialized with a value")
        }
      }
    }

    private fun checkSameNameClassAndInstanceFinals(classLevelFinals: Map<String?, PyTargetExpression>,
                                                    initAttributes: Map<String, PyTargetExpression>) {
      initAttributes.forEach { (name, initAttribute) ->
        val sameNameClassLevelFinal = classLevelFinals[name]

        if (sameNameClassLevelFinal != null && isFinal(initAttribute)) {
          if (sameNameClassLevelFinal.hasAssignedValue()) {
            registerProblem(initAttribute, "Already declared name could not be redefined as 'Final'")
          }
          else {
            val message = "Either instance attribute or class attribute could be type hinted as 'Final'"
            registerProblem(sameNameClassLevelFinal, message)
            registerProblem(initAttribute, message)
          }
        }
      }
    }

    private fun checkOverridingInheritedFinalWithNewOne(cls: PyClass) {
      val (newClassFinals, newInstanceFinals) = getDeclaredClassAndInstanceFinals(cls)

      val notRegisteredClassFinals = newClassFinals.keys.toMutableSet()
      val notRegisteredInstanceFinals = newInstanceFinals.keys.toMutableSet()
      if (notRegisteredClassFinals.isEmpty() && notRegisteredInstanceFinals.isEmpty()) return

      for (ancestor in cls.getAncestorClasses(myTypeEvalContext)) {
        val (inheritedClassFinals, inheritedInstanceFinals) = getDeclaredClassAndInstanceFinals(ancestor)

        checkOverridingInheritedFinalWithNewOne(newClassFinals, inheritedClassFinals, ancestor.name, notRegisteredClassFinals)
        checkOverridingInheritedFinalWithNewOne(newInstanceFinals, inheritedInstanceFinals, ancestor.name, notRegisteredInstanceFinals)

        if (notRegisteredClassFinals.isEmpty() && notRegisteredInstanceFinals.isEmpty()) break
      }
    }

    private fun checkOverridingInheritedFinalWithNewOne(newFinals: Map<String, PyTargetExpression>,
                                                        inheritedFinals: Map<String, PyTargetExpression>,
                                                        ancestorName: String?,
                                                        notRegistered: MutableSet<String>) {
      if (notRegistered.isEmpty()) return

      for (commonFinal in newFinals.keys.intersect(inheritedFinals.keys)) {
        registerProblem(newFinals[commonFinal], "'$ancestorName.$commonFinal' is 'Final' and could not be overridden")
        notRegistered.remove(commonFinal)
      }
    }

    private fun checkInstanceFinalsOutsideInit(method: PyFunction) {
      if (PyUtil.isInitMethod(method)) return

      val instanceAttributes = mutableMapOf<String, PyTargetExpression>()
      PyClassImpl.collectInstanceAttributes(method, instanceAttributes)
      instanceAttributes.values.forEach {
        if (isFinal(it)) registerProblem(it, "'Final' attribute should be declared in class body or '__init__'")
      }
    }

    private fun checkFinalReassignment(target: PyQualifiedExpression) {
      val qualifierType = target.qualifier?.let { myTypeEvalContext.getType(it) }
      if (qualifierType is PyClassType && !qualifierType.isDefinition) {
        checkInstanceFinalReassignment(target, qualifierType.pyClass)
        return
      }

      // TODO: revert back to PyUtil#multiResolveTopPriority when resolve into global statement is implemented
      val resolved = when (target) {
        is PyReferenceOwner -> target.getReference(resolveContext).multiResolve(false).mapNotNull { it.element }
        else -> PyUtil.multiResolveTopPriority(target, resolveContext)
      }

      if (resolved.any { it is PyTargetExpression && isFinal(it) }) {
        registerProblem(target, "'${target.name}' is 'Final' and could not be reassigned")
        return
      }

      for (e in resolved) {
        if (myTypeEvalContext.maySwitchToAST(e) &&
            e.parent.let { it is PyNonlocalStatement || it is PyGlobalStatement } &&
            PyUtil.multiResolveTopPriority(e, resolveContext).any { it is PyTargetExpression && isFinal(it) }) {
          registerProblem(target, "'${target.name}' is 'Final' and could not be reassigned")
          return
        }
      }

      if (!target.isQualified) {
        val scopeOwner = ScopeUtil.getScopeOwner(target)
        if (scopeOwner is PyClass) {
          checkInheritedClassFinalReassignmentOnClassLevel(target, scopeOwner)
        }
      }
    }

    private fun checkInstanceFinalReassignment(target: PyQualifiedExpression, cls: PyClass) {
      val name = target.name ?: return

      val classAttribute = cls.findClassAttribute(name, false, myTypeEvalContext)
      if (classAttribute != null && !classAttribute.hasAssignedValue() && isFinal(classAttribute)) {
        if (target is PyTargetExpression &&
            ScopeUtil.getScopeOwner(target).let { it is PyFunction && PyUtil.turnConstructorIntoClass(it) == cls }) {
          return
        }
        registerProblem(target, "'$name' is 'Final' and could not be reassigned")
      }

      for (ancestor in cls.getAncestorClasses(myTypeEvalContext)) {
        val inheritedClassAttribute = ancestor.findClassAttribute(name, false, myTypeEvalContext)
        if (inheritedClassAttribute != null && !inheritedClassAttribute.hasAssignedValue() && isFinal(inheritedClassAttribute)) {
          registerProblem(target, "'${ancestor.name}.$name' is 'Final' and could not be reassigned")
          return
        }
      }

      for (current in (sequenceOf(cls) + cls.getAncestorClasses(myTypeEvalContext).asSequence())) {
        val init = current.findMethodByName(PyNames.INIT, false, myTypeEvalContext)
        if (init != null) {
          val attributesInInit = mutableMapOf<String, PyTargetExpression>()
          PyClassImpl.collectInstanceAttributes(init, attributesInInit)
          if (attributesInInit[name]?.let { it != target && isFinal(it) } == true) {
            val qualifier = if (cls == current) "" else "${current.name}."
            registerProblem(target, "'$qualifier$name' is 'Final' and could not be reassigned")
            break
          }
        }
      }
    }

    private fun checkInheritedClassFinalReassignmentOnClassLevel(target: PyQualifiedExpression, cls: PyClass) {
      val name = target.name ?: return

      for (ancestor in cls.getAncestorClasses(myTypeEvalContext)) {
        val ancestorClassAttribute = ancestor.findClassAttribute(name, false, myTypeEvalContext)

        if (ancestorClassAttribute != null && ancestorClassAttribute.hasAssignedValue() && isFinal(ancestorClassAttribute)) {
          registerProblem(target, "'${ancestor.name}.$name' is 'Final' and could not be reassigned")
          break
        }
      }
    }

    private fun checkFinalIsOuterMost(node: PyReferenceExpression) {
      if (isTopLevelInAnnotationOrTypeComment(node)) return
      (node.parent as? PySubscriptionExpression)?.let {
        if (it.operand == node && isTopLevelInAnnotationOrTypeComment(it)) return
      }

      if (isInAnnotationOrTypeComment(node) && resolvesToFinal(node)) {
        registerProblem(node, "'Final' could only be used as the outermost type")
      }
    }

    private fun checkFinalInsideLoop(loop: PyLoopStatement) {
      loop.acceptChildren(
        object : PyRecursiveElementVisitor() {
          override fun visitElement(element: PsiElement) {
            if (element !is ScopeOwner) super.visitElement(element)
          }

          override fun visitPyForStatement(node: PyForStatement) {}

          override fun visitPyWhileStatement(node: PyWhileStatement) {}

          override fun visitPyTargetExpression(node: PyTargetExpression) {
            if (isFinal(node)) registerProblem(node, "'Final' could not be used inside a loop")
          }
        }
      )
    }

    private fun isFinal(decoratable: PyDecoratable) = isFinal(decoratable, myTypeEvalContext)

    private fun <T> isFinal(node: T): Boolean where T : PyAnnotationOwner, T : PyTypeCommentOwner {
      return isFinal(node, myTypeEvalContext)
    }

    private fun resolvesToFinal(expression: PyExpression?): Boolean {
      return expression is PyReferenceExpression && eventuallyResolvesToFinal(expression, myTypeEvalContext)
    }

    private fun isTopLevelInAnnotationOrTypeComment(node: PyExpression): Boolean {
      val parent = node.parent
      if (parent is PyAnnotation) return true
      if (parent is PyExpressionStatement && parent.parent is PyDocstringFile) return true
      if (parent is PyParameterTypeList) return true
      if (parent is PyFunctionTypeAnnotation) return true
      return false
    }
  }
}
