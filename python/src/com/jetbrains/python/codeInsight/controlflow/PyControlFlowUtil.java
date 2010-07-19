package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
public class PyControlFlowUtil {
  public static void iteratePrev(final int statInstruction,
                                 @NotNull final Instruction[] instructions,
                                 @NotNull final Function<Instruction, Operation> closure) {
    final ControlFlowUtil.Stack stack = new ControlFlowUtil.Stack(instructions.length);
    final boolean[] visited = new boolean[instructions.length];

    stack.push(statInstruction);
    while (!stack.isEmpty()) {
      final int num = stack.pop();
      if (visited[num]){
        continue;
      }
      visited[num] = true;
      final Instruction instr = instructions[num];
      final Operation nextOperation = closure.fun(instr);
      if (nextOperation == Operation.CONTINUE) {
        continue;
      } else if (nextOperation == Operation.BREAK) {
        break;
      }
      for (Instruction pred : instr.allPred()) {
        stack.push(pred.num());
      }
    }
  }

  public static enum Operation {
    CONTINUE, BREAK, NEXT
  }
}
