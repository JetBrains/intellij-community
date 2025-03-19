package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@ApiStatus.Internal
public class PyDataFlow {
  private final TypeEvalContext myTypeEvalContext;
  private final Instruction[] myInstructions;
  private final boolean[] myReachability;

  public PyDataFlow(@NotNull ControlFlow controlFlow, @NotNull TypeEvalContext context) {
    myTypeEvalContext = context;
    myInstructions = controlFlow.getInstructions();
    myReachability = new boolean[myInstructions.length];
    buildReachability();
  }

  private void buildReachability() {
    Queue<Instruction> toBeProcessed = new ArrayDeque<>();
    toBeProcessed.add(myInstructions[0]);
    while (!toBeProcessed.isEmpty()) {
      Instruction instruction = toBeProcessed.poll();
      myReachability[instruction.num()] = true;
      for (var successor : getReachableSuccessors(instruction)) {
        if (!myReachability[successor.num()]) {
          toBeProcessed.add(successor);
        }
      }
    }
  }

  private @NotNull Collection<Instruction> getReachableSuccessors(@NotNull Instruction instruction) {
    if (instruction instanceof CallInstruction ci && ci.isNoReturnCall(myTypeEvalContext)) return List.of();
    if (instruction instanceof PyWithContextExitInstruction wi && !wi.isSuppressingExceptions(myTypeEvalContext)) return List.of();
    return instruction.allSucc();
  }

  public boolean isUnreachable(@NotNull Instruction instruction) {
    return !myReachability[instruction.num()];
  }

  public static boolean isUnreachable(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    final var scope = ScopeUtil.getScopeOwner(element);
    if (scope != null) {
      final var flow = ControlFlowCache.getControlFlow(scope).getInstructions();
      int idx = ControlFlowUtil.findInstructionNumberByElement(flow, element);
      if (idx < 0) return false;
      return ControlFlowCache.getDataFlow(scope, context).isUnreachable(flow[idx]);
    }
    return false;
  }
}
