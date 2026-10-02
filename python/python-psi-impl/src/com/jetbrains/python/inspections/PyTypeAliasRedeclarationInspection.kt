// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInsight.controlflow.ControlFlowUtil
import com.intellij.codeInsight.controlflow.Instruction
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiNameIdentifierOwner
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PythonUiService
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.PyTypeAliasStatement
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

/**
 * Annotates type alias re-declarations
 */
@ApiStatus.Internal
class PyTypeAliasRedeclarationInspection : PyInspection() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession
  ): PsiElementVisitor {
    val context = PyInspectionVisitor.getContext(session)
    if (context.usesExternalTypeEngine) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return Visitor(holder, context = context)
  }

  private class Visitor(holder: ProblemsHolder?, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {
    override fun visitPyTypeAliasStatement(node: PyTypeAliasStatement) {
      reportRedeclaration(node)
    }

    fun reportRedeclaration(element: PsiNameIdentifierOwner) {
      val name = element.getName()
      var writeElement: PsiElement? = null
      if (name != null) {
        val owner = ScopeUtil.getScopeOwner(element)
        if (owner != null) {
          val instructions = ControlFlowCache.getControlFlow(owner).getInstructions()
          val startInstruction = ControlFlowUtil.findInstructionNumberByElement(instructions, element)
          if (startInstruction >= 0) {
            ControlFlowUtil.iteratePrev(startInstruction, instructions) { instruction: Instruction? ->
              if (instruction is ReadWriteInstruction && instruction.num() != startInstruction) {
                if (name == instruction.name) {
                  val originalElement = instruction.element
                  if (originalElement != null) {
                    if (instruction.access.isWriteAccess && originalElement !== element) {
                      writeElement = originalElement
                      return@iteratePrev ControlFlowUtil.Operation.BREAK
                    }
                  }
                }
              }
              ControlFlowUtil.Operation.NEXT
            }
          }
        }
      }

      if (writeElement == null) {
        return
      }
      val quickFixes: MutableList<LocalQuickFix> = ArrayList()
      val quickFix = PythonUiService.getInstance().createPyRenameElementQuickFix(element)
      if (quickFix != null) {
        quickFixes.add(quickFix)
      }
      val identifier = element.getNameIdentifier()
      registerProblem(identifier ?: element,
                      PyPsiBundle.problemMessage("INSP.redeclared.type.alias", name),
                      ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                      *quickFixes.toTypedArray())
    }
  }
}
