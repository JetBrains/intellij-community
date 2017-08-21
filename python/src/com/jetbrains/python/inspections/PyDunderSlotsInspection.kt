/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.inspections

import com.google.common.collect.Iterables
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.PyClassType

class PyDunderSlotsInspection : PyInspection() {

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = Visitor(holder, session)

  private class Visitor(holder: ProblemsHolder, session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {

    override fun visitPyClass(node: PyClass?) {
      super.visitPyClass(node)

      if (node != null && LanguageLevel.forElement(node).isAtLeast(LanguageLevel.PYTHON30)) {
        val slots = findSlotsValue(node)

        when (slots) {
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

    override fun visitPyTargetExpression(node: PyTargetExpression?) {
      super.visitPyTargetExpression(node)

      if (node != null) {
        checkAttributeExpression(node)
      }
    }

    private fun findSlotsValue(pyClass: PyClass): PyExpression? {
      val target = pyClass.findClassAttribute(PyNames.SLOTS, false, myTypeEvalContext)
      val value = target?.findAssignedValue()

      return PyPsiUtils.flattenParens(value)
    }

    private fun processSlot(pyClass: PyClass, slot: PyStringLiteralExpression) {
      val name = slot.stringValue

      if (pyClass.findClassAttribute(name, false, myTypeEvalContext) != null) {
        registerProblem(slot, "'$name' in __slots__ conflicts with class variable")
      }
    }

    private fun checkAttributeExpression(target: PyTargetExpression) {
      val targetName = target.name
      val qualifier = target.qualifier

      if (targetName == null || qualifier == null) {
        return
      }

      val qualifierType = myTypeEvalContext.getType(qualifier)
      if (qualifierType is PyClassType && !qualifierType.isDefinition) {
        val qualifierClass = qualifierType.pyClass

        if (!attributeIsWritable(qualifierClass, targetName)) {
          registerProblem(target, "'${qualifierClass.name}' object attribute '$targetName' is read-only")
        }
      }
    }

    private fun attributeIsWritable(cls: PyClass, name: String): Boolean {
      /*
      The only difference between Py2 and Py3+ is that the following case is not highlighted in Py3+:

      class A:
          attr = "attr"
          __slots__ = ("attr")

      A().attr

      Py3+ raises ValueError about conflict between __slots__ and class variable.
      This case is handled above by com.jetbrains.python.inspections.PyDunderSlotsInspection.Visitor.processSlot method.
      */
      if (LanguageLevel.forElement(cls).isOlderThan(LanguageLevel.PYTHON30)) {
        return attributeIsWritableInPy2(cls, name)
      }
      else {
        return attributeIsWritableInPy3(cls, name)
      }
    }

    private fun attributeIsWritableInPy2(cls: PyClass, name: String): Boolean {
      val slots = cls.getSlots(myTypeEvalContext)
      return slots == null ||
             slots.contains(PyNames.DICT) ||
             slots.contains(name) && cls.findClassAttribute(name, true, myTypeEvalContext) == null
    }

    private fun attributeIsWritableInPy3(cls: PyClass, name: String): Boolean {
      var classAttrIsFound = false
      var slotIsFound = false

      for (c in Iterables.concat(listOf(cls), cls.getAncestorClasses(myTypeEvalContext))) {
        if (PyUtil.isObjectClass(c)) continue

        val ownSlots = c.ownSlots

        if (ownSlots == null || ownSlots.contains(PyNames.DICT)) return true

        if (!classAttrIsFound) {
          classAttrIsFound = c.findClassAttribute(name, false, myTypeEvalContext) != null
          if (ownSlots.contains(name)) {
            if (classAttrIsFound) return true
            slotIsFound = true
          }
        }
      }

      return slotIsFound && !classAttrIsFound
    }
  }
}

