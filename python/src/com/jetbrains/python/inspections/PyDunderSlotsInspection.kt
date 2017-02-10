/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.resolve.PyResolveContext
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
      val target = pyClass.findClassAttribute(PyNames.SLOTS, false, myTypeEvalContext) as? PyTargetExpression
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
        val reference = target.getReference(PyResolveContext.noImplicits().withTypeEvalContext(myTypeEvalContext))
        val qualifierClass = qualifierType.pyClass

        val classWithReadOnlyAttr = PyUtil
            .multiResolveTopPriority(reference)
            .asSequence()
            .filterIsInstance<PyTargetExpression>()
            .map { declaration -> declaration.containingClass }
            .filterNotNull()
            .find { declaringClass -> !attributeIsWritable(qualifierClass, declaringClass, targetName) }

        if (classWithReadOnlyAttr != null) {
          registerProblem(target, "'${qualifierClass.name}' object attribute '$targetName' is read-only")
        }
      }
    }

    private fun attributeIsWritable(qualifierClass: PyClass, declaringClass: PyClass, targetName: String): Boolean {
      return attributeIsWritableInClass(qualifierClass, declaringClass, targetName) ||
          qualifierClass
              .getAncestorClasses(myTypeEvalContext)
              .asSequence()
              .filter { ancestorClass -> !PyUtil.isObjectClass(ancestorClass) }
              .any { ancestorClass -> attributeIsWritableInClass(ancestorClass, declaringClass, targetName) }
    }

    private fun attributeIsWritableInClass(cls: PyClass, declaringClass: PyClass, targetName: String): Boolean {
      val ownSlots = cls.ownSlots

      if (ownSlots == null || ownSlots.contains(PyNames.DICT)) {
        return true
      }

      if (!cls.equals(declaringClass) || !ownSlots.contains(targetName)) {
        return false
      }

      return LanguageLevel.forElement(declaringClass).isAtLeast(LanguageLevel.PYTHON30) ||
          declaringClass.findClassAttribute(targetName, false, myTypeEvalContext) == null
    }
  }
}

