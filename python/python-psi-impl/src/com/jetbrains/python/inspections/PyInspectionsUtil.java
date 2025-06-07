// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.controlflow.*;
import com.jetbrains.python.psi.PyStatementListContainer;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class PyInspectionsUtil {

  /**
   * Collects a list of unreachable elements, iterating through CFG backwards
   */
  @ApiStatus.Internal
  public static @NotNull List<PsiElement> collectUnreachable(@NotNull ScopeOwner owner, @Nullable PsiElement anchor, @NotNull TypeEvalContext context) {
    final ControlFlow flow = ControlFlowCache.getControlFlow(owner);
    final Instruction[] instructions = flow.getInstructions();
    final List<PsiElement> unreachable = new ArrayList<>();
    final int start = anchor != null ? ControlFlowUtil.findInstructionNumberByElement(instructions, anchor) : instructions.length - 1;
    if (start >= 0) {
      ControlFlowUtil.iteratePrev(start, instructions, instruction -> {
        if (getReachablePredecessors(instruction, context).isEmpty() && instruction.num() != 0) {
          ContainerUtil.addIfNotNull(unreachable, getRelevantElement(instruction));
        }
        return ControlFlowUtil.Operation.NEXT;
      });
    }
    return unreachable;
  }

  private static @NotNull List<Instruction> getReachablePredecessors(@NotNull Instruction instruction, @NotNull TypeEvalContext context) {
    // TODO: merge this with PyDataFlow
    return ContainerUtil.filter(instruction.allPred(), it -> {
      if (it instanceof CallInstruction ci && ci.isNoReturnCall(context)) return false;
      if (it instanceof PyWithContextExitInstruction wi && !wi.isSuppressingExceptions(context)) return false;
      if (it instanceof ReadWriteInstruction rw && rw.getAccess().isAssertTypeAccess()) {
        var type = rw.getType(context, null);
        return !(type != null && type.get() instanceof PyNeverType);
      }
      return true;
    });
  }

  private static @Nullable PsiElement getRelevantElement(@NotNull Instruction instruction) {
    if (instruction instanceof PyWithContextExitInstruction) {
      return null;
    }
    if (instruction instanceof ReadWriteInstruction rw && rw.getAccess().isAssertTypeAccess()) {
      return null;
    }
    PsiElement element = instruction.getElement();
    if (element instanceof PyStatementListContainer) {
      return ((PyStatementListContainer)element).getStatementList();
    }
    return element;
  }

  private PyInspectionsUtil() {
  }
}
