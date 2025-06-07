// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.functionTypeComments.psi.PyFunctionTypeAnnotation
import com.jetbrains.python.codeInsight.functionTypeComments.psi.PyParameterTypeList
import com.jetbrains.python.codeInsight.parseDataclassParameters
import com.jetbrains.python.codeInsight.typeHints.PyTypeHintFile
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.*
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyClassImpl
import com.jetbrains.python.psi.search.PySuperMethodsSearch
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.pyi.PyiUtil
import com.jetbrains.python.refactoring.PyDefUseUtil

class PyFinalInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = Visitor(holder, PyInspectionVisitor.getContext(session))

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyClass(node: PyClass) {
      super.visitPyClass(node)

      node.getSuperClasses(myTypeEvalContext).filter { isFinal(it) }.let { finalSuperClasses ->
        if (finalSuperClasses.isEmpty()) return@let

        @NlsSafe val superClassList = finalSuperClasses.joinToString { "'${it.name}'" }
        registerProblem(node.nameIdentifier,
                        PyPsiBundle.message("INSP.final.super.classes.are.marked.as.final.and.should.not.be.subclassed",
                                            superClassList, finalSuperClasses.size))
      }

      if (!PyiUtil.isInsideStub(node)) {
        val (classLevelFinals, initAttributes) = getClassLevelFinalsAndInitAttributes(node)
        if (!isDataclass(node)) {
          checkClassLevelFinalsAreInitialized(classLevelFinals, initAttributes)
        }
        checkSameNameClassAndInstanceFinals(classLevelFinals, initAttributes)
      }

      checkOverridingInheritedFinalWithNewOne(node)
    }

    override fun visitPyFunction(node: PyFunction) {
      super.visitPyFunction(node)

      val cls = node.containingClass
      if (cls != null) {
        val isOverload = PyiUtil.isOverload(node, myTypeEvalContext)
        if (!isOverload ||
            PyiUtil.getImplementation(node, myTypeEvalContext) == null &&
            PyiUtil.getOverloads(node, myTypeEvalContext).firstOrNull() === node) {
          PySuperMethodsSearch
            .search(node, myTypeEvalContext)
            .asIterable()
            .asSequence()
            .filterIsInstance<PyFunction>()
            .firstOrNull { isFinal(it) }
            ?.let {
              @NlsSafe val qualifiedName = it.qualifiedName ?: (it.containingClass?.name + "." + it.name)
              registerProblem(node.nameIdentifier,
                              PyPsiBundle.message("INSP.final.method.marked.as.final.should.not.be.overridden", qualifiedName))
            }
        }
        if (!PyiUtil.isInsideStub(node)) {
          checkInstanceFinalsOutsideInit(node)
        }

        if (PyKnownDecoratorUtil.hasAbstractDecorator(node, myTypeEvalContext)) {
          if (isFinal(node)) {
            registerProblem(node.nameIdentifier, PyPsiBundle.message("INSP.final.final.could.not.be.mixed.with.abstract.decorators"))
          }
          else if (isFinal(cls)) {
            val message = PyPsiBundle.message("INSP.final.final.class.could.not.contain.abstract.methods")
            registerProblem(node.nameIdentifier, message)
            registerProblem(cls.nameIdentifier, message)
          }
        }
        else if (isFinal(node) && isFinal(cls)) {
          registerProblem(node.nameIdentifier, PyPsiBundle.message("INSP.final.no.need.to.mark.method.in.final.class.as.final"),
                          ProblemHighlightType.WEAK_WARNING)
        }
      }
      else if (isFinal(node)) {
        registerProblem(node.nameIdentifier, PyPsiBundle.message("INSP.final.non.method.function.could.not.be.marked.as.final"))
      }

      getFunctionTypeAnnotation(node)?.let { comment ->
        if (comment.parameterTypeList.parameterTypes.any { resolvesToFinal(if (it is PySubscriptionExpression) it.operand else it) }) {
          registerProblem(node.typeComment,
                          PyPsiBundle.message("INSP.final.final.could.not.be.used.in.annotations.for.function.parameters"))
        }
      }

      getReturnTypeAnnotation(node, myTypeEvalContext)?.let {
        if (resolvesToFinal(if (it is PySubscriptionExpression) it.operand else it)) {
          registerProblem(node.typeComment ?: node.annotation,
                          PyPsiBundle.message("INSP.final.final.could.not.be.used.in.annotation.for.function.return.value"))
        }
      }
    }

    override fun visitPyTargetExpression(node: PyTargetExpression) {
      super.visitPyTargetExpression(node)

      val parent = PsiTreeUtil.getParentOfType(node, PyStatement::class.java)
      if (parent is PyTypeDeclarationStatement || parent is PyGlobalStatement || parent is PyNonlocalStatement) {
        node.annotation?.value?.let {
          if (PyiUtil.isInsideStub(node) || ScopeUtil.getScopeOwner(node) is PyClass) {
            if (resolvesToFinal(it)) {
              registerProblem(it,
                              PyPsiBundle.message("INSP.final.if.assigned.value.omitted.there.should.be.explicit.type.argument.to.final"))
            }
          }
          else {
            if (resolvesToFinal(if (it is PySubscriptionExpression) it.operand else it)) {
              registerProblem(node, PyPsiBundle.message("INSP.final.final.name.should.be.initialized.with.value"))
            }
          }
        }
      }
      else if (!isFinal(node)) {
        checkFinalReassignment(node)
      }

      if (isFinal(node) && PyUtil.multiResolveTopPriority(node, resolveContext).any {
          it != node && !PyDefUseUtil.isDefinedBefore(node, it)
        }) {
        registerProblem(node, PyPsiBundle.message("INSP.final.already.declared.name.could.not.be.redefined.as.final"))
      }
    }

    override fun visitPyNamedParameter(node: PyNamedParameter) {
      super.visitPyNamedParameter(node)

      if (isFinal(node)) {
        registerProblem(node.annotation?.value ?: node.typeComment,
                        PyPsiBundle.message("INSP.final.final.could.not.be.used.in.annotations.for.function.parameters"))
      }
    }

    override fun visitPyReferenceExpression(node: PyReferenceExpression) {
      super.visitPyReferenceExpression(node)

      checkFinalIsOuterMost(node)
    }

    override fun visitPySubscriptionExpression(node: PySubscriptionExpression) {
      if (!resolvesToFinal(node.operand)) return
      val indexExpression = node.indexExpression ?: return
      if (indexExpression is PyTupleExpression && indexExpression.elements.size > 1) {
        registerProblem(indexExpression, PyPsiBundle.message("INSP.final.can.only.be.parameterized.with.one.type"))
      }
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
        PyClassImpl.collectInstanceAttributes(init, attributesInInit)
        attributesInInit.keys.removeAll(instanceFinals.keys)
        instanceFinals += attributesInInit.filterValues { isFinal(it) }
      }

      return Pair(classFinals, instanceFinals)
    }

    private fun checkClassLevelFinalsAreInitialized(classLevelFinals: Map<String?, PyTargetExpression>,
                                                    initAttributes: Map<String, PyTargetExpression>) {
      classLevelFinals.forEach { (name, psi) ->
        if (!psi.hasAssignedValue() && name !in initAttributes) {
          registerProblem(psi, PyPsiBundle.message("INSP.final.final.name.should.be.initialized.with.value"))
        }
      }
    }

    private fun checkSameNameClassAndInstanceFinals(classLevelFinals: Map<String?, PyTargetExpression>,
                                                    initAttributes: Map<String, PyTargetExpression>) {
      initAttributes.forEach { (name, initAttribute) ->
        val sameNameClassLevelFinal = classLevelFinals[name]

        if (sameNameClassLevelFinal != null && isFinal(initAttribute)) {
          if (sameNameClassLevelFinal.hasAssignedValue()) {
            registerProblem(initAttribute, PyPsiBundle.message("INSP.final.already.declared.name.could.not.be.redefined.as.final"))
          }
          else {
            val message = PyPsiBundle.message("INSP.final.either.instance.attribute.or.class.attribute.could.be.type.hinted.as.final")
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
        @NlsSafe val qualifiedName = "$ancestorName.$commonFinal"
        registerProblem(newFinals[commonFinal], PyPsiBundle.message("INSP.final.final.attribute.could.not.be.overridden", qualifiedName))
        notRegistered.remove(commonFinal)
      }
    }

    private fun checkInstanceFinalsOutsideInit(method: PyFunction) {
      if (PyUtil.isInitMethod(method)) return

      val instanceAttributes = mutableMapOf<String, PyTargetExpression>()
      PyClassImpl.collectInstanceAttributes(method, instanceAttributes)
      instanceAttributes.values.forEach {
        if (isFinal(it)) registerProblem(it, PyPsiBundle.message("INSP.final.final.attribute.should.be.declared.in.class.body.or.init"))
      }
    }

    private fun checkFinalReassignment(target: PyQualifiedExpression) {
      val qualifierType = target.qualifier?.let { myTypeEvalContext.getType(it) }
      if (qualifierType is PyClassType && !qualifierType.isDefinition) {
        checkInstanceFinalReassignment(target, qualifierType.pyClass)
        return
      }

      if (qualifierType is PyClassType && qualifierType.isDefinition) {
        checkClassFinalReassignment(target, qualifierType.pyClass)
        return
      }

      // TODO: revert back to PyUtil#multiResolveTopPriority when resolve into global statement is implemented
      val resolved = when (target) {
        is PyReferenceOwner -> target.getReference(resolveContext).multiResolve(false).mapNotNull { it.element }
        else -> PyUtil.multiResolveTopPriority(target, resolveContext)
      }.toMutableList()

      val scopeOwner = ScopeUtil.getScopeOwner(target);
      if (!target.isQualified && scopeOwner != null) {
        // multiResolve finds last assignments, but we need all earlier assignments
        val scope = ControlFlowCache.getScope(scopeOwner)
        resolved += scope.getNamedElements(target.referencedName, false)
        target.name?.let { name ->
          resolved += scope.importedNameDefiners.flatMap { it.multiResolveName(name).mapNotNull { it.element } }
        }
      }


      for (e in resolved.mapNotNull { it as? PyTargetExpression }) {
        if (isFinal(e) && !PyDefUseUtil.isDefinedBefore(target, e)) {
          registerProblem(target, PyPsiBundle.message("INSP.final.final.target.could.not.be.reassigned", target.name))
          return
        }
        if (e.parent.let { it is PyNonlocalStatement || it is PyGlobalStatement } &&
          PyUtil.multiResolveTopPriority(e, resolveContext).any { it is PyTargetExpression && isFinal(it) }) {
          registerProblem(target, PyPsiBundle.message("INSP.final.final.target.could.not.be.reassigned", target.name))
          return
        }
      }

      if (!target.isQualified && scopeOwner is PyClass) {
        checkInheritedClassFinalReassignmentOnClassLevel(target, scopeOwner)
      }
    }

    private fun checkClassFinalReassignment(target: PyQualifiedExpression, cls: PyClass) {
      val name = target.name ?: return
      val classAttribute = cls.findClassAttribute(name, true, myTypeEvalContext)
      if (classAttribute != null) {
        var isFinal = isFinal(classAttribute)
        if (!isFinal && isDataclass(cls)) {
          isFinal = resolvesToClassVarFinal(classAttribute.annotation?.value)
        }
        if (isFinal) {
          registerProblem(target, PyPsiBundle.message("INSP.final.final.target.could.not.be.reassigned", name))
        }
      }
    }

    private fun checkInstanceFinalReassignment(target: PyQualifiedExpression, cls: PyClass) {
      val name = target.name ?: return

      val classAttribute = cls.findClassAttribute(name, false, myTypeEvalContext)
      if (classAttribute != null && isFinal(classAttribute)) {
        if (!classAttribute.hasAssignedValue() &&
            target is PyTargetExpression &&
            ScopeUtil.getScopeOwner(target).let { it is PyFunction && PyUtil.turnConstructorIntoClass(it) == cls }) {
          return
        }
        registerProblem(target, PyPsiBundle.message("INSP.final.final.target.could.not.be.reassigned", name))
      }

      for (ancestor in cls.getAncestorClasses(myTypeEvalContext)) {
        val inheritedClassAttribute = ancestor.findClassAttribute(name, false, myTypeEvalContext)
        if (inheritedClassAttribute != null && !inheritedClassAttribute.hasAssignedValue() && isFinal(inheritedClassAttribute)) {
          @NlsSafe val qualifiedName = "${ancestor.name}.$name"
          registerProblem(target, PyPsiBundle.message("INSP.final.final.target.could.not.be.reassigned", qualifiedName))
          return
        }
      }

      for (current in (sequenceOf(cls) + cls.getAncestorClasses(myTypeEvalContext).asSequence())) {
        val init = current.findMethodByName(PyNames.INIT, false, myTypeEvalContext)
        if (init != null) {
          val attributesInInit = mutableMapOf<String, PyTargetExpression>()
          PyClassImpl.collectInstanceAttributes(init, attributesInInit)
          if (attributesInInit[name]?.let { it != target && isFinal(it) } == true) {
            @NlsSafe val qualifiedName = (if (cls == current) "" else "${current.name}.") + name
            registerProblem(target, PyPsiBundle.message("INSP.final.final.target.could.not.be.reassigned", qualifiedName))
            break
          }
        }
      }
    }

    private fun checkInheritedClassFinalReassignmentOnClassLevel(target: PyQualifiedExpression, cls: PyClass) {
      val name = target.name ?: return
      if (PyUtil.isClassPrivateName(name)) return

      for (ancestor in cls.getAncestorClasses(myTypeEvalContext)) {
        val ancestorClassAttribute = ancestor.findClassAttribute(name, false, myTypeEvalContext)

        if (ancestorClassAttribute != null && ancestorClassAttribute.hasAssignedValue() && isFinal(ancestorClassAttribute)) {
          @NlsSafe val qualifiedName = "${ancestor.name}.$name"
          registerProblem(target, PyPsiBundle.message("INSP.final.final.target.could.not.be.reassigned", qualifiedName))
          break
        }
      }
    }

    private fun checkFinalIsOuterMost(node: PyReferenceExpression) {
      if (isTopLevelInAnnotationOrTypeComment(node)) return
      (node.parent as? PySubscriptionExpression)?.let {
        if (it.operand == node && isTopLevelInAnnotationOrTypeComment(it)) return
      }

      val scopeOwner = ScopeUtil.getScopeOwner(node)
      if (scopeOwner is PyClass && isDataclass(scopeOwner)
          && resolvesToClassVarFinal(node.parent.parent as? PyExpression)) {
        return
      }

      if (isInsideTypeHint(node, myTypeEvalContext) && resolvesToFinal(node)) {
        registerProblem(node, PyPsiBundle.message("INSP.final.final.could.only.be.used.as.outermost.type"))
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
            if (isFinal(node)) registerProblem(node, PyPsiBundle.message("INSP.final.final.could.not.be.used.inside.loop"))
          }
        }
      )
    }

    private fun isFinal(decoratable: PyDecoratable) = isFinal(decoratable, myTypeEvalContext)

    private fun <T> isFinal(node: T): Boolean where T : PyAnnotationOwner, T : PyTypeCommentOwner {
      return isFinal(node, myTypeEvalContext)
    }

    private fun resolvesToFinal(expression: PyExpression?): Boolean {
      return expression is PyReferenceExpression &&
             resolveToQualifiedNames(expression, myTypeEvalContext).any { it == FINAL || it == FINAL_EXT }
    }

    private fun resolvesToClassVar(expression: PyExpression): Boolean {
      return (expression is PyReferenceExpression) &&
             resolveToQualifiedNames(expression, myTypeEvalContext).any { it == CLASS_VAR }
    }

    private fun resolvesToClassVarFinal(expression: PyExpression?): Boolean {
      return (expression as? PySubscriptionExpression)?.let {
        if (resolvesToClassVar(it.operand)) {
          if (it.indexExpression is PySubscriptionExpression) {
            return resolvesToFinal((it.indexExpression as PySubscriptionExpression).operand)
          }
          return resolvesToFinal(it.indexExpression)
        }
        return false
      } ?: false
    }

    private fun isDataclass(cls: PyClass): Boolean {
      return parseDataclassParameters(cls, myTypeEvalContext) != null
    }

    private fun isTopLevelInAnnotationOrTypeComment(node: PyExpression): Boolean {
      val parent = node.parent
      if (parent is PyAnnotation) return true
      if (parent is PyExpressionStatement && parent.parent is PyTypeHintFile) return true
      if (parent is PyParameterTypeList) return true
      if (parent is PyFunctionTypeAnnotation) return true
      return false
    }
  }
}
