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

/**
 * Annotates type alias re-declarations
 */
class PyTypeAliasRedeclarationInspection : PyInspection() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession
  ): PsiElementVisitor {
    return Visitor(holder, PyInspectionVisitor.getContext(session))
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
                      PyPsiBundle.message("INSP.redeclared.type.alias", name),
                      ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                      null,
                      *quickFixes.toTypedArray())
    }
  }
}
