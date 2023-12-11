// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PyStatementListContainer;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects unreachable code using control flow graph
 */
public final class PyUnreachableCodeInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@NotNull ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitElement(@NotNull final PsiElement element) {
      if (element instanceof ScopeOwner) {
        final ControlFlow flow = ControlFlowCache.getControlFlow((ScopeOwner)element);
        final Instruction[] instructions = flow.getInstructions();
        final List<PsiElement> unreachable = new ArrayList<>();
        if (instructions.length > 0) {
          ControlFlowUtil.iteratePrev(instructions.length - 1, instructions, instruction -> {
            if (instruction.allPred().isEmpty() && !PyInspectionsUtil.isFirstInstruction(instruction)) {
              unreachable.add(unwrapStatementListContainer(instruction.getElement()));
            }
            return ControlFlowUtil.Operation.NEXT;
          });
        }
        for (PsiElement e : unreachable) {
          registerProblem(e, PyPsiBundle.message("INSP.unreachable.code"));
        }
      }
    }

    private static @Nullable PsiElement unwrapStatementListContainer(@Nullable PsiElement element) {
      return element instanceof PyStatementListContainer ? ((PyStatementListContainer)element).getStatementList() : element;
    }
  }
}
