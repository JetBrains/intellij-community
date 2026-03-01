// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.buildHtmlChunk
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.Processor
import com.intellij.util.containers.SortedList
import com.intellij.util.containers.sequenceOfNotNull
import com.intellij.util.containers.tail
import com.intellij.xml.util.XmlStringUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.ast.PyAstFunction
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.codeInsight.typing.isProtocol
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKnownDecorator
import com.jetbrains.python.psi.PyKnownDecoratorUtil
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyClassImpl
import com.jetbrains.python.psi.types.PyCallableParameterListTypeImpl
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.pyi.PyiFile
import com.jetbrains.python.pyi.PyiUtil
import org.jetbrains.annotations.Nls
import java.util.EnumSet

class PyOverloadsInspection : PyInspection() {

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor = Visitor(holder, PyInspectionVisitor.getContext(session))

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {
    private val OVERLOADS_ANALYSIS_LIMIT = 30

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

      checkOverloadsOverlapping(overloads)

      var requiresImplementation = true
      if (owner.containingFile is PyiFile) {
        requiresImplementation = false
      }
      else if (owner is PyClass) {
        if (owner.isProtocol(myTypeEvalContext)) {
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
        overloads
          .asSequence()
          .filter {
            val overloadInputSignature = PyCallableParameterListTypeImpl(it.getParameters(myTypeEvalContext))
            val implementationInputSignature = PyCallableParameterListTypeImpl(implementation.getParameters(myTypeEvalContext))

            !PyTypeChecker.match(overloadInputSignature, implementationInputSignature, myTypeEvalContext)
          }
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

    private fun checkOverloadsOverlapping(overloads: List<PyFunction>) {
      // This analysis is N^2 so limited to a reasonable number of overloads to prevent performance issues
      if (overloads.size !in 2..OVERLOADS_ANALYSIS_LIMIT) return

      // __get__ method should be special-cased
      if (overloads.firstOrNull()?.name == PyNames.DUNDER_GET) return

      for (i in overloads.indices) {
        val current = overloads[i]
        val currCallableType = myTypeEvalContext.getType(current) as? PyCallableType ?: continue
        val currParams = currCallableType.getParameters(myTypeEvalContext) ?: continue
        val currReturnType = currCallableType.getReturnType(myTypeEvalContext)
        val currInputSignature = PyCallableParameterListTypeImpl(currParams)

        for (j in (i + 1) until overloads.size) {
          val next = overloads[j]
          val nextCallableType = myTypeEvalContext.getType(next) as? PyCallableType ?: continue
          val nextParams = nextCallableType.getParameters(myTypeEvalContext) ?: continue
          val nextReturnType = nextCallableType.getReturnType(myTypeEvalContext)

          val nextOverloadInputSignature = PyCallableParameterListTypeImpl(nextParams)

          if (PyTypeChecker.match(nextOverloadInputSignature, currInputSignature, myTypeEvalContext)) {
            val signatureRepresentation = PythonDocumentationProvider.getTypeName(nextCallableType, myTypeEvalContext)
            val message =
              buildMessage(PyPsiBundle.message("INSP.overloads.overload.overlapped.by.a.broader.type", i + 1),
                           signatureRepresentation)
            registerProblem(next.nameIdentifier, message)
          }
          else if (PyTypeChecker.match(currInputSignature, nextOverloadInputSignature, myTypeEvalContext)) {
            if (!PyTypeChecker.match(nextReturnType, currReturnType, myTypeEvalContext)) {
              val signatureRepresentation = PythonDocumentationProvider.getTypeName(currCallableType, myTypeEvalContext)
              val message =
                buildMessage(PyPsiBundle.message("INSP.overloads.overload.overlaps.with.incompatible.return.type", j + 1),
                             signatureRepresentation)
              registerProblem(current.nameIdentifier, message)
            }
          }
        }
      }
    }

    @InspectionMessage
    private fun buildMessage(
      @Nls msg: String,
      @NlsSafe signature: String,
    ): String = XmlStringUtil
      .wrapInHtml(buildHtmlChunk {
        append(msg)
        append(HtmlChunk.br())
        append(PyPsiBundle.message("INSP.overloads.overload.conflicting.signature", signature))
      }.toString())
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
