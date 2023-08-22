// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyDunderSlotsInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = Visitor(
    holder,PyInspectionVisitor.getContext(session))

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyClass(node: PyClass) {
      super.visitPyClass(node)

      if (!LanguageLevel.forElement(node).isPython2) {
        when (val slots = findSlotsValue(node)) {
          is PySequenceExpression -> slots
            .elements
            .asSequence()
            .filterIsInstance<PyStringLiteralExpression>()
            .forEach {
              processSlot(node, it)
            }
          is PyStringLiteralExpression -> processSlot(node, slots)
        }
      }
    }

    override fun visitPyTargetExpression(node: PyTargetExpression) {
      super.visitPyTargetExpression(node)

      checkAttributeExpression(node)
    }

    private fun findSlotsValue(pyClass: PyClass): PyExpression? {
      val target = pyClass.findClassAttribute(PyNames.SLOTS, false, myTypeEvalContext)
      val value = target?.findAssignedValue()

      return PyPsiUtils.flattenParens(value)
    }

    private fun processSlot(pyClass: PyClass, slot: PyStringLiteralExpression) {
      val name = slot.stringValue

      val classAttribute = pyClass.findClassAttribute(name, false, myTypeEvalContext)
      if (classAttribute != null && classAttribute.hasAssignedValue()) {
        registerProblem(slot, PyPsiBundle.message("INSP.dunder.slots.name.in.slots.conflicts.with.class.variable", name))
      }
    }

    private fun checkAttributeExpression(target: PyTargetExpression) {
      val targetName = target.name
      val qualifier = target.qualifier

      if (targetName == null || qualifier == null) {
        return
      }

      val qualifierType = myTypeEvalContext.getType(qualifier)
      if (qualifierType is PyClassType && !qualifierType.isAttributeWritable(targetName, myTypeEvalContext)) {
        registerProblem(target, PyPsiBundle.message("INSP.dunder.slots.class.object.attribute.read.only", qualifierType.name, targetName))
      }
    }
  }
}

