// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects unreachable code using control flow graph
 */
public class PyUnreachableCodeInspection extends PyInspection {
  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.unreachable.code");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@NotNull ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitElement(final PsiElement element) {
      if (element instanceof ScopeOwner) {
        final ControlFlow flow = ControlFlowCache.getControlFlow((ScopeOwner)element);
        final Instruction[] instructions = flow.getInstructions();
        final List<PsiElement> unreachable = new ArrayList<>();
        if (instructions.length > 0) {
          ControlFlowUtil.iteratePrev(instructions.length - 1, instructions, instruction -> {
            if (instruction.allPred().isEmpty() && !isFirstInstruction(instruction)) {
              unreachable.add(instruction.getElement());
            }
            return ControlFlowUtil.Operation.NEXT;
          });
        }
        for (PsiElement e : unreachable) {
          registerProblem(e, PyBundle.message("INSP.unreachable.code"));
        }
      }
    }
  }

  public static boolean hasAnyInterruptedControlFlowPaths(@NotNull PsiElement element) {
    final ScopeOwner owner = ScopeUtil.getScopeOwner(element);
    if (owner != null) {
      final ControlFlow flow = ControlFlowCache.getControlFlow(owner);
      final Instruction[] instructions = flow.getInstructions();
      final int start = ControlFlowUtil.findInstructionNumberByElement(instructions, element);
      if (start >= 0) {
        final Ref<Boolean> resultRef = Ref.create(false);
        ControlFlowUtil.iteratePrev(start, instructions, instruction -> {
          if (instruction.allPred().isEmpty() && !isFirstInstruction(instruction)) {
            resultRef.set(true);
            return ControlFlowUtil.Operation.BREAK;
          }
          return ControlFlowUtil.Operation.NEXT;
        });
        return resultRef.get();
      }
    }
    return false;
  }

  private static boolean isFirstInstruction(Instruction instruction) {
    return instruction.num() == 0;
  }
}
