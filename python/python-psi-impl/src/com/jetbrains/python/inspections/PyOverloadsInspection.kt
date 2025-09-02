// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.Processor
import com.intellij.util.containers.SortedList
import com.intellij.util.containers.sequenceOfNotNull
import com.intellij.util.containers.tail
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.ast.PyAstFunction
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.codeInsight.typing.isProtocol
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyClassImpl
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.pyi.PyiFile
import com.jetbrains.python.pyi.PyiUtil
import java.util.EnumSet

class PyOverloadsInspection : PyInspection() {

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor = Visitor(holder, PyInspectionVisitor.getContext(session))

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyClass(node: PyClass) {
      processScope(node) { node.visitMethods(it, false, myTypeEvalContext) }
    }

    override fun visitPyFile(node: PyFile) {
      processScope(node) { processor -> node.topLevelFunctions.forEach { processor.process(it) } }
    }

    private fun processScope(owner: ScopeOwner, processorUsage: (GroupingFunctionsByNameProcessor) -> Unit) {
      val processor = GroupingFunctionsByNameProcessor()
      processorUsage(processor)
      processor.result.values.forEach { processSameNameFunctions(owner, it) }
    }

    private fun processSameNameFunctions(owner: ScopeOwner, functions: List<PyFunction>) {
      val (overloads, implementations) = functions.partition { PyiUtil.isOverload(it, myTypeEvalContext) }

      if (overloads.isEmpty()) return

      if (overloads.size == 1) {
        registerProblem(overloads[0].nameIdentifier,
                        PyPsiBundle.message("INSP.overloads.at.least.two.overloads.must.be.present",
                                            if (owner is PyClass) 1 else 0))
      }

      val implementation = implementations.lastOrNull()

      checkClassMethodAndStaticMethodConsistency(overloads, implementation)

      checkOverrideAndFinal(overloads, implementation)

      var requiresImplementation = true
      if (owner.containingFile is PyiFile) {
        requiresImplementation = false
      }
      else if (owner is PyClass) {
        if (isProtocol(owner, myTypeEvalContext)) {
          requiresImplementation = false
        }
        else {
          if (PyClassImpl.canHaveAbstractMethods(owner, myTypeEvalContext)) {
            if (overloads.all { PyKnownDecoratorUtil.hasAbstractDecorator(it, myTypeEvalContext) }) {
              requiresImplementation = false
            }
          }
        }
      }

      if (requiresImplementation && implementation !== functions.last()) {
        val problemElement = if (implementation == null) functions.first() else functions.last()
        registerProblem(problemElement.nameIdentifier,
                        PyPsiBundle.message("INSP.overloads.series.overloads.should.always.be.followed.by.implementation",
                                            if (owner is PyClass) 1 else 0))
      }

      if (implementation != null) {
        functions
          .asSequence()
          .filter { isIncompatibleOverload(implementation, it) }
          .forEach {
            registerProblem(it.nameIdentifier,
                            PyPsiBundle.message("INSP.overloads.this.overload.signature.not.compatible.with.implementation",
                                                if (owner is PyClass) 1 else 0))
          }
      }
    }

    private fun checkClassMethodAndStaticMethodConsistency(overloads: List<PyFunction>, implementation: PyFunction?) {
      val modifiers = overloads.mapNotNullTo(EnumSet.noneOf(PyAstFunction.Modifier::class.java)) { it.modifier }
      for (function in overloads.asSequence() + sequenceOfNotNull(implementation)) {
        val modifier = function.modifier
        if (modifiers.contains(PyAstFunction.Modifier.CLASSMETHOD) && modifier != PyAstFunction.Modifier.CLASSMETHOD) {
          registerProblem(function.nameIdentifier, PyPsiBundle.message("INSP.overloads.use.classmethod.inconsistently"))
        }
        if (modifiers.contains(PyAstFunction.Modifier.STATICMETHOD) && modifier != PyAstFunction.Modifier.STATICMETHOD) {
          registerProblem(function.nameIdentifier, PyPsiBundle.message("INSP.overloads.use.staticmethod.inconsistently"))
        }
      }
    }

    private fun checkOverrideAndFinal(overloads: List<PyFunction>, implementation: PyFunction?) {
      if (implementation == null) {
        for (overload in overloads.tail()) {
          if (isOverride(overload)) {
            registerProblem(overload.nameIdentifier,
                            PyPsiBundle.message("INSP.overloads.override.should.be.placed.only.on.the.first.overload"))
          }
          if (PyTypingTypeProvider.isFinal(overload, myTypeEvalContext)) {
            registerProblem(overload.nameIdentifier,
                            PyPsiBundle.message("INSP.overloads.final.should.be.placed.only.on.the.first.overload"))
          }
        }
      }
      else {
        for (overload in overloads) {
          if (isOverride(overload)) {
            registerProblem(overload.nameIdentifier,
                            PyPsiBundle.message("INSP.overloads.override.should.be.placed.on.the.implementation"))
          }
          if (PyTypingTypeProvider.isFinal(overload, myTypeEvalContext)) {
            registerProblem(overload.nameIdentifier,
                            PyPsiBundle.message("INSP.overloads.final.should.be.placed.on.the.implementation"))
          }
        }
      }
    }

    private fun isOverride(function: PyFunction): Boolean {
      val decoratorList = function.decoratorList ?: return false
      return decoratorList.decorators.any { decorator ->
        PyKnownDecoratorUtil.asKnownDecorators(decorator, myTypeEvalContext).any {
          it == PyKnownDecorator.TYPING_OVERRIDE || it == PyKnownDecorator.TYPING_EXTENSIONS_OVERRIDE
        }
      }
    }

    private fun isIncompatibleOverload(implementation: PyFunction, overload: PyFunction): Boolean {
      return implementation != overload &&
             PyiUtil.isOverload(overload, myTypeEvalContext) &&
             !PyUtil.isSignatureCompatibleTo(implementation, overload, myTypeEvalContext)
    }
  }

  private class GroupingFunctionsByNameProcessor : Processor<PyFunction> {

    val result: MutableMap<String, MutableList<PyFunction>> = HashMap()

    override fun process(t: PyFunction?): Boolean {
      val name = t?.name
      if (name != null) {
        result
          .getOrPut(name) { SortedList { f1, f2 -> f1.textOffset - f2.textOffset } }
          .add(t)
      }

      return true
    }
  }
}
