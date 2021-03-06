// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.Processor
import com.intellij.util.containers.SortedList
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.pyi.PyiFile
import com.jetbrains.python.pyi.PyiUtil
import java.util.*

class PyOverloadsInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = Visitor(
    holder, session)

  private class Visitor(holder: ProblemsHolder, session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {

    override fun visitPyClass(node: PyClass) {
      if (node.containingFile is PyiFile) return

      super.visitPyClass(node)

      processScope(node, { node.visitMethods(it, false, myTypeEvalContext) })
    }

    override fun visitPyFile(node: PyFile) {
      if (node is PyiFile) return

      super.visitPyFile(node)

      processScope(node, { processor -> node.topLevelFunctions.forEach { processor.process(it) } })
    }

    private fun processScope(owner: ScopeOwner, processorUsage: (GroupingFunctionsByNameProcessor) -> Unit) {
      val processor = GroupingFunctionsByNameProcessor()
      processorUsage(processor)
      processor.result.values.forEach { processSameNameFunctions(owner, it) }
    }

    private fun processSameNameFunctions(owner: ScopeOwner, functions: List<PyFunction>) {
      if (functions.find { PyiUtil.isOverload(it, myTypeEvalContext) } == null) return

      val implementation = functions.lastOrNull { !PyiUtil.isOverload(it, myTypeEvalContext) }

      if (implementation == null) {
        functions
          .maxBy { it.textOffset }
          ?.let {
            registerProblem(it.nameIdentifier, if (owner is PyClass) {
              PyPsiBundle.message("INSP.overloads.series.overload.decorated.methods.should.always.be.followed.by.implementation")
            }
            else {
              PyPsiBundle.message("INSP.overloads.series.overload.decorated.functions.should.always.be.followed.by.implementation")
            })
          }
      }
      else {
        if (implementation != functions.last()) {
          registerProblem(functions.last().nameIdentifier, if (owner is PyClass) {
            PyPsiBundle.message("INSP.overloads.series.overload.decorated.methods.should.always.be.followed.by.implementation")
          }
          else {
            PyPsiBundle.message("INSP.overloads.series.overload.decorated.functions.should.always.be.followed.by.implementation")
          })
        }

        functions
          .asSequence()
          .filter { isIncompatibleOverload(implementation, it) }
          .forEach {
            registerProblem(it.nameIdentifier, if (owner is PyClass) {
              PyPsiBundle.message("INSP.overloads.this.method.overload.signature.not.compatible.with.implementation")
            }
            else {
              PyPsiBundle.message("INSP.overloads.this.function.overload.signature.not.compatible.with.implementation")
            })
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
          .getOrPut(name, { SortedList<PyFunction> { f1, f2 -> f1.textOffset - f2.textOffset } })
          .add(t)
      }

      return true
    }
  }
}
