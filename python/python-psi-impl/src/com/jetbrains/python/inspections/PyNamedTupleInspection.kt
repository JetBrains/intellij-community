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
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.PyDataclassNames.Dataclasses
import com.jetbrains.python.codeInsight.parseDataclassParameters
import com.jetbrains.python.codeInsight.stdlib.PyNamedTupleTypeProvider
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyNamedTupleInspection : PyInspection() {

  object Helper {
    fun inspectFieldsOrder(
      cls: PyClass,
      classFieldsFilter: (PyClass) -> Boolean,
      checkInheritedOrder: Boolean,
      context: TypeEvalContext,
      callback: (PsiElement, @InspectionMessage String, ProblemHighlightType) -> Unit,
      fieldsFilter: (PyTargetExpression) -> Boolean = { true },
      hasAssignedValue: (PyTargetExpression) -> Boolean = PyTargetExpression::hasAssignedValue,
    ) {
      val fieldsProcessor = if (classFieldsFilter(cls)) processFields(cls, fieldsFilter, hasAssignedValue, context) else null

      if ((fieldsProcessor == null || fieldsProcessor.fieldsWithoutDefaultValue.isEmpty()) && !checkInheritedOrder) return

      val ancestors = cls.getAncestorClasses(context)
      val attributeNames = mutableSetOf<String>()
      val ancestorKinds = mutableListOf<Ancestor>()
      for (ancestor in ancestors.reversed()) {
        val kind = if (!classFieldsFilter(ancestor)) {
          Ancestor.FILTERED
        }
        else {
          val processor = processFields(ancestor, fieldsFilter, hasAssignedValue, context)
          if (processor.fieldsWithDefaultValue.isNotEmpty()) {
            Ancestor.HAS_FIELD_WITH_DEFAULT_VALUE
          }
          else if (processor.fieldsWithoutDefaultValue.isNotEmpty() && !attributeNames.containsAll(processor.fieldsWithoutDefaultValue.map { field -> field.name })) {
            Ancestor.HAS_FIELD_WITHOUT_DEFAULT_VALUE
          }
          else {
            Ancestor.FILTERED
          }
        }

        ancestorKinds.add(kind)
        attributeNames.addAll(ancestor.classAttributes.mapNotNull { attribute -> attribute.name })
      }
      ancestorKinds.reverse()

      if (checkInheritedOrder) {
        var seenAncestorHavingFieldWithDefaultValue: PyClass? = null
        for ((ancestor, ancestorKind) in (ancestors zip ancestorKinds).asReversed()) {
          if (ancestorKind == Ancestor.HAS_FIELD_WITH_DEFAULT_VALUE) {
            seenAncestorHavingFieldWithDefaultValue = ancestor
          }
          else if (ancestorKind == Ancestor.HAS_FIELD_WITHOUT_DEFAULT_VALUE && seenAncestorHavingFieldWithDefaultValue != null) {
            val msg = PyPsiBundle.message("INSP.named.tuple.default.value.order.inherited", ancestor.name,
                                          seenAncestorHavingFieldWithDefaultValue.name)
            callback(cls.superClassExpressionList!!, msg, ProblemHighlightType.GENERIC_ERROR)
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

            val msg = PyPsiBundle.message("INSP.named.tuple.default.value.order.superclass", ancestorNames)
            callback(fieldsWithoutDefaultNotOverriden.first(), msg, ProblemHighlightType.GENERIC_ERROR)
          }
        }
        fieldsProcessor.fieldsWithDefaultValue
          .takeWhile { PyPsiUtils.isBefore(it, fieldsWithoutDefaultNotOverriden.last()) }
          .forEach { callback(it, PyPsiBundle.message("INSP.named.tuple.default.value.order.local"), ProblemHighlightType.GENERIC_ERROR) }
      }
    }
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
    Visitor(holder, PyInspectionVisitor.getContext(session))
}

private fun processFields(
  cls: PyClass,
  filter: (PyTargetExpression) -> Boolean,
  hasAssignedValue: (PyTargetExpression) -> Boolean,
  context: TypeEvalContext,
): LocalFieldsProcessor {
  val isDataclass = parseDataclassParameters(cls, context) != null
  val fieldsProcessor = LocalFieldsProcessor(filter, hasAssignedValue, isDataclass, context)
  cls.processClassLevelDeclarations(fieldsProcessor)
  return fieldsProcessor
}

private enum class Ancestor {
  FILTERED, HAS_FIELD_WITH_DEFAULT_VALUE, HAS_FIELD_WITHOUT_DEFAULT_VALUE
}


private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

  override fun visitPyClass(node: PyClass) {
    super.visitPyClass(node)

    if (LanguageLevel.forElement(node).isAtLeast(LanguageLevel.PYTHON36) &&
        PyNamedTupleTypeProvider.Helper.isTypingNamedTupleDirectInheritor(node, myTypeEvalContext)
    ) {
      PyNamedTupleInspection.Helper.inspectFieldsOrder(node, { it == node }, false,
                                                       myTypeEvalContext,
                                                       this::registerProblem)
    }
  }
}

private class LocalFieldsProcessor(
  private val filter: (PyTargetExpression) -> Boolean,
  private val hasAssignedValue: (PyTargetExpression) -> Boolean,
  private val isDataclass: Boolean,
  private val context: TypeEvalContext,
) : PsiScopeProcessor {
  val fieldsWithDefaultValue = mutableListOf<PyTargetExpression>()
  val fieldsWithoutDefaultValue = mutableListOf<PyTargetExpression>()
  var kwOnlyMarkerVisited: Boolean = false

  override fun execute(element: PsiElement, state: ResolveState): Boolean {
    if (element is PyTargetExpression && filter(element)) {
      if (isDataclass && isKwOnlyMarker(element, context)) {
        kwOnlyMarkerVisited = true
      }
      else if (kwOnlyMarkerVisited) {
        return true
      }
      else if (hasAssignedValue(element)) {
        fieldsWithDefaultValue.add(element)
      }
      else {
        fieldsWithoutDefaultValue.add(element)
      }
    }
    return true
  }

  private fun isKwOnlyMarker(field: PyTargetExpression, context: TypeEvalContext): Boolean {
    return (context.getType(field) as? PyClassType)?.classQName == Dataclasses.DATACLASSES_KW_ONLY
  }
}