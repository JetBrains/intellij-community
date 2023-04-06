// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import com.jetbrains.python.codeInsight.stdlib.PyNamedTupleTypeProvider
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.TypeEvalContext

class PyNamedTupleInspection : PyInspection() {

  companion object {
    fun inspectFieldsOrder(cls: PyClass,
                           classFieldsFilter: (PyClass) -> Boolean,
                           checkInheritedOrder: Boolean,
                           context: TypeEvalContext,
                           callback: (PsiElement, @InspectionMessage String, ProblemHighlightType) -> Unit,
                           fieldsFilter: (PyTargetExpression) -> Boolean = { true },
                           hasAssignedValue: (PyTargetExpression) -> Boolean = PyTargetExpression::hasAssignedValue) {
      val fieldsProcessor = if (classFieldsFilter(cls)) processFields(cls, fieldsFilter, hasAssignedValue) else null

      if ((fieldsProcessor == null || fieldsProcessor.fieldsWithoutDefaultValue.isEmpty()) && !checkInheritedOrder) return

      val ancestors = cls.getAncestorClasses(context)
      val ancestorKinds = ancestors.map {
        if (!classFieldsFilter(it)) {
          Ancestor.FILTERED
        }
        else {
          val processor = processFields(it, fieldsFilter, hasAssignedValue)
          if (processor.fieldsWithDefaultValue.isNotEmpty()) {
            Ancestor.HAS_FIELD_WITH_DEFAULT_VALUE
          }
          else if (processor.fieldsWithoutDefaultValue.isNotEmpty()) {
            Ancestor.HAS_FIELD_WITHOUT_DEFAULT_VALUE
          }
          else {
            Ancestor.FILTERED
          }
        }
      }

      if (checkInheritedOrder) {
        var seenAncestorHavingFieldWithDefaultValue: PyClass? = null
        for ((ancestor, ancestorKind) in (ancestors zip ancestorKinds).asReversed()) {
          if (ancestorKind == Ancestor.HAS_FIELD_WITH_DEFAULT_VALUE) {
            seenAncestorHavingFieldWithDefaultValue = ancestor
          }
          else if (ancestorKind == Ancestor.HAS_FIELD_WITHOUT_DEFAULT_VALUE && seenAncestorHavingFieldWithDefaultValue != null) {
            callback(
              cls.superClassExpressionList!!,
              "Inherited non-default argument(s) defined in ${ancestor.name} follows " +
              "inherited default argument defined in ${seenAncestorHavingFieldWithDefaultValue.name}",
              ProblemHighlightType.GENERIC_ERROR
            )
            break
          }
        }
      }

      if (fieldsProcessor == null) return

      val ancestorFieldNames = ancestors.flatMap { it.classAttributes }.map { it.name }.toSet()
      val fieldsWithoutDefaultNotOverriden = fieldsProcessor.fieldsWithoutDefaultValue.filterNot { it.name in ancestorFieldNames }

      if (fieldsWithoutDefaultNotOverriden.isNotEmpty()) {
        if (Ancestor.HAS_FIELD_WITH_DEFAULT_VALUE in ancestorKinds) {
          val classNameElement = cls.nameIdentifier
          if (classNameElement != null) {
            val ancestorNames = (ancestors zip ancestorKinds)
              .filter { it.second == Ancestor.HAS_FIELD_WITH_DEFAULT_VALUE }
              .joinToString { "'${it.first.name}'" }

            callback(classNameElement,
                     "Non-default argument(s) follows default argument(s) defined in $ancestorNames",
                     ProblemHighlightType.GENERIC_ERROR)
          }
        }
        val lastFieldWithoutDefault = fieldsWithoutDefaultNotOverriden.last()
        fieldsProcessor.fieldsWithDefaultValue
          .takeWhile { PyPsiUtils.isBefore(it, lastFieldWithoutDefault) }
          .forEach {
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
      FILTERED, HAS_FIELD_WITH_DEFAULT_VALUE, HAS_FIELD_WITHOUT_DEFAULT_VALUE
    }
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
    Visitor(holder, PyInspectionVisitor.getContext(session))

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyClass(node: PyClass) {
      super.visitPyClass(node)

      if (LanguageLevel.forElement(node).isAtLeast(LanguageLevel.PYTHON36) &&
          PyNamedTupleTypeProvider.isTypingNamedTupleDirectInheritor(node, myTypeEvalContext)) {
        inspectFieldsOrder(node, { it == node }, false,
                           myTypeEvalContext,
                           this::registerProblem)
      }
    }
  }

  private class LocalFieldsProcessor(private val filter: (PyTargetExpression) -> Boolean,
                                     private val hasAssignedValue: (PyTargetExpression) -> Boolean) : PsiScopeProcessor {
    val fieldsWithDefaultValue = mutableListOf<PyTargetExpression>()
    val fieldsWithoutDefaultValue = mutableListOf<PyTargetExpression>()

    override fun execute(element: PsiElement, state: ResolveState): Boolean {
      if (element is PyTargetExpression && filter(element)) {
        if (hasAssignedValue(element)) {
          fieldsWithDefaultValue.add(element)
        }
        else {
          fieldsWithoutDefaultValue.add(element)
        }
      }
      return true
    }
  }
}
