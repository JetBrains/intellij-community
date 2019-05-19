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
import com.jetbrains.python.psi.types.TypeEvalContext
import java.util.*

class PyNamedTupleInspection : PyInspection() {

  companion object {
    fun inspectFieldsOrder(cls: PyClass,
                           ancestorsFilter: (PyClass) -> Boolean,
                           checkInheritedOrder: Boolean,
                           context: TypeEvalContext,
                           callback: (PsiElement, String, ProblemHighlightType) -> Unit,
                           fieldsFilter: (PyTargetExpression) -> Boolean = { true },
                           hasAssignedValue: (PyTargetExpression) -> Boolean = PyTargetExpression::hasAssignedValue) {
      val fieldsProcessor = processFields(cls, fieldsFilter, hasAssignedValue)

      val ancestors = cls.getAncestorClasses(context)
      val ancestorsFields = ancestors.map {
        when {
          !ancestorsFilter(it) -> Ancestor.FILTERED
          processFields(it, fieldsFilter, hasAssignedValue).fieldsWithDefaultValue.isNotEmpty() -> Ancestor.HAS_FIELD_WITH_DEFAULT_VALUE
          else -> Ancestor.HAS_NOT_FIELD_WITH_DEFAULT_VALUE
        }
      }

      if (checkInheritedOrder) {
        var seenAncestorHavingFieldWithDefaultValue: PyClass? = null
        for (ancestorAndFields in ancestors.zip(ancestorsFields).asReversed()) {
          if (ancestorAndFields.second == Ancestor.HAS_FIELD_WITH_DEFAULT_VALUE) seenAncestorHavingFieldWithDefaultValue = ancestorAndFields.first
          else if (ancestorAndFields.second == Ancestor.HAS_NOT_FIELD_WITH_DEFAULT_VALUE && seenAncestorHavingFieldWithDefaultValue != null) {
            callback(
              cls.superClassExpressionList!!,
              "Inherited non-default argument(s) defined in ${ancestorAndFields.first.name} follows " +
              "inherited default argument defined in ${seenAncestorHavingFieldWithDefaultValue.name}",
              ProblemHighlightType.GENERIC_ERROR
            )

            break
          }
        }
      }

      val lastFieldWithoutDefaultValue = fieldsProcessor.lastFieldWithoutDefaultValue
      if (lastFieldWithoutDefaultValue != null) {
        if (ancestorsFields.contains(Ancestor.HAS_FIELD_WITH_DEFAULT_VALUE)) {
          cls.nameIdentifier?.let { name ->
            val ancestorsNames = ancestors
              .asSequence()
              .zip(ancestorsFields.asSequence())
              .filter { it.second == Ancestor.HAS_FIELD_WITH_DEFAULT_VALUE }
              .joinToString { "'${it.first.name}'" }

            callback(name,
                     "Non-default argument(s) follows default argument(s) defined in $ancestorsNames",
                     ProblemHighlightType.GENERIC_ERROR)
          }
        }

        fieldsProcessor.fieldsWithDefaultValue.headSet(lastFieldWithoutDefaultValue).forEach {
          callback(it,
                   "Fields with a default value must come after any fields without a default.",
                   ProblemHighlightType.GENERIC_ERROR)
        }
      }
    }

    private fun processFields(cls: PyClass,
                              filter: (PyTargetExpression) -> Boolean,
                              hasAssignedValue: (PyTargetExpression) -> Boolean): LocalFieldsProcessor {
      val fieldsProcessor = LocalFieldsProcessor(filter, hasAssignedValue)
      cls.processClassLevelDeclarations(fieldsProcessor)

      return fieldsProcessor
    }

    private enum class Ancestor {
      FILTERED, HAS_FIELD_WITH_DEFAULT_VALUE, HAS_NOT_FIELD_WITH_DEFAULT_VALUE
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
        inspectFieldsOrder(node, { false }, false, myTypeEvalContext, this::registerProblem)
      }
    }
  }

  private class LocalFieldsProcessor(private val filter: (PyTargetExpression) -> Boolean,
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