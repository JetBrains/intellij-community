package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;

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
    Deque<Instruction> stack = new ArrayDeque<>();
    stack.push(myInstructions[0]);

    while (!stack.isEmpty()) {
      Instruction instruction = stack.pop();
      int instructionNum = instruction.num();

      if (myReachability[instructionNum]) {
        continue; // Already visited
      }

      myReachability[instructionNum] = true;

      for (Instruction successor : getReachableSuccessors(instruction)) {
        if (!myReachability[successor.num()]) {
          stack.push(successor);
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
    if (instruction.num() >= myReachability.length) return false;
    return !myReachability[instruction.num()];
  }

  public static boolean isUnreachable(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    final var scope = ScopeUtil.getScopeOwner(element);
    if (scope != null) {
      final var flow = ControlFlowCache.getControlFlow(scope).getInstructions();
      int idx = findInstructionNumberByElement(flow, element);
      if (idx < 0) return false;
      return ControlFlowCache.getDataFlow(scope, context).isUnreachable(flow[idx]);
    }
    return false;
  }

  /**
   * Like ControlFlowUtil.findInstructionNumberByElement, but does not use ProgressManager.checkCanceled()
   */
  public static int findInstructionNumberByElement(final Instruction[] flow, final PsiElement element){
    for (int i=0;i<flow.length;i++) {
      if (element == flow[i].getElement()){
        return i;
      }
    }
    return -1;
  }
}
