// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.parseDataclassParameters
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyDunderSlotsInspection : PyInspection() {

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor = Visitor(
    holder, PyInspectionVisitor.getContext(session))

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {

    override fun visitPyClass(node: PyClass) {
      super.visitPyClass(node)

      val params = parseDataclassParameters(node, myTypeEvalContext)
      if (params != null && params.slots) {
        val explicitSlotsAttr = node.findClassAttribute(PyNames.SLOTS, false, myTypeEvalContext)
        if (explicitSlotsAttr != null) {
          val anchor = params.slotsArgument ?: node.nameIdentifier ?: node
          val message = PyPsiBundle.message("INSP.dunder.slots.enabled.twice")
          if (params.slotsArgument != null) {
            registerProblem(anchor, message, RemoveSlotsKwargQuickFix())
          }
          else {
            registerProblem(anchor, message)
          }
        }
      }

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
        val message = PyPsiBundle.message("INSP.dunder.slots.name.in.slots.conflicts.with.class.variable", name)
        registerProblem(slot, message)
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
        val message = PyPsiBundle.message("INSP.dunder.slots.class.object.missing.attribute", qualifierType.name, targetName)
        registerProblem(target, message)
      }
    }
  }

  private class RemoveSlotsKwargQuickFix : ModCommandQuickFix() {
    override fun getFamilyName(): String = PyPsiBundle.message("QFIX.dunder.slots.enabled.twice")

    override fun perform(project: Project, descriptor: ProblemDescriptor): ModCommand {
      val element = descriptor.psiElement
      val kwArg = element.parentOfType<PyKeywordArgument>()
                  ?: element as? PyKeywordArgument
                  ?: return ModCommand.nop()

      // Only remove if it's the 'slots' kwarg inside an argument list
      if (kwArg.keyword != "slots") return ModCommand.nop()
      if (kwArg.parent !is PyArgumentList) return ModCommand.nop()

      return ModCommand.psiUpdate(kwArg) { kwArgCopy ->
        // Remove neighboring comma to keep argument list syntax correct
        val commaAfter = PsiTreeUtil.skipWhitespacesForward(kwArgCopy)
          ?.takeIf { it.node.elementType == PyTokenTypes.COMMA }
        val commaBefore = PsiTreeUtil.skipWhitespacesBackward(kwArgCopy)
          ?.takeIf { it.node.elementType == PyTokenTypes.COMMA }

        // Prefer removing the trailing comma, otherwise remove the leading one
        if (commaAfter != null) {
          commaAfter.delete()
          kwArgCopy.delete()
        }
        else if (commaBefore != null) {
          kwArgCopy.delete()
          commaBefore.delete()
        }
        else {
          kwArgCopy.delete()
        }
      }
    }
  }

}

