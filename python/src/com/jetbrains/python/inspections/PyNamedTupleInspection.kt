// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import com.jetbrains.python.codeInsight.stdlib.PyNamedTupleTypeProvider
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyTargetExpression
import java.util.*

class PyNamedTupleInspection : PyInspection() {

  companion object {
    fun inspectFieldsOrder(cls: PyClass,
                           callback: (PsiElement, String, ProblemHighlightType) -> Unit,
                           filter: (PyTargetExpression) -> Boolean = { true },
                           hasAssignedValue: (PyTargetExpression) -> Boolean = PyTargetExpression::hasAssignedValue) {
      val fieldsProcessor = FieldsProcessor(filter, hasAssignedValue)

      cls.processClassLevelDeclarations(fieldsProcessor)

      registerErrorOnTargetsAboveBound(fieldsProcessor.lastFieldWithoutDefaultValue,
                                       fieldsProcessor.fieldsWithDefaultValue,
                                       "Fields with a default value must come after any fields without a default.",
                                       callback)
    }

    private fun registerErrorOnTargetsAboveBound(bound: PyTargetExpression?,
                                                 targets: TreeSet<PyTargetExpression>,
                                                 message: String,
                                                 callback: (PsiElement, String, ProblemHighlightType) -> Unit) {
      if (bound != null) {
        targets
          .headSet(bound)
          .forEach { callback(it, message, ProblemHighlightType.GENERIC_ERROR) }
      }
    }
  }

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = Visitor(holder, session)

  private class Visitor(holder: ProblemsHolder, session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {

    override fun visitPyClass(node: PyClass?) {
      super.visitPyClass(node)

      if (node != null &&
          LanguageLevel.forElement(node).isAtLeast(LanguageLevel.PYTHON36) &&
          PyNamedTupleTypeProvider.isTypingNamedTupleDirectInheritor(node, myTypeEvalContext)) {
        inspectFieldsOrder(node, this::registerProblem)
      }
    }
  }

  private class FieldsProcessor(private val filter: (PyTargetExpression) -> Boolean,
                                private val hasAssignedValue: (PyTargetExpression) -> Boolean) : PsiScopeProcessor {

    val lastFieldWithoutDefaultValue: PyTargetExpression?
      get() = lastFieldWithoutDefaultValueBox.result
    val fieldsWithDefaultValue: TreeSet<PyTargetExpression>

    private val lastFieldWithoutDefaultValueBox: MaxBy<PyTargetExpression>

    init {
      val offsetComparator = compareBy(PyTargetExpression::getTextOffset)
      lastFieldWithoutDefaultValueBox = MaxBy(offsetComparator)
      fieldsWithDefaultValue = TreeSet(offsetComparator)
    }

    override fun execute(element: PsiElement, state: ResolveState): Boolean {
      if (element is PyTargetExpression && filter(element)) {
        when {
          hasAssignedValue(element) -> fieldsWithDefaultValue.add(element)
          else -> lastFieldWithoutDefaultValueBox.apply(element)
        }
      }

      return true
    }
  }

  private class MaxBy<T>(private val comparator: Comparator<T>) {

    var result: T? = null
      private set

    fun apply(t: T) {
      if (result == null || comparator.compare(result, t) < 0) {
        result = t
      }
    }
  }
}