package com.jetbrains.python.refactoring;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.controlflow.ReadInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.controlflow.WriteInstruction;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;

/**
 * @author Dennis.Ushakov
 */
public class PyDefUseUtil {
  private PyDefUseUtil() {}

  @NotNull
  public static PyElement[] getLatestDefs(ScopeOwner block, PyTargetExpression var, PyElement anchor) {
    final ControlFlow controlFlow = block.getControlFlow();
    final Instruction[] instructions = controlFlow.getInstructions();
    final int instr = ControlFlowUtil.findInstructionNumberByElement(instructions, anchor);
    assert instr >= 0;
    final boolean[] visited = new boolean[instructions.length];
    final Collection<PyElement> result = new HashSet<PyElement>();
    getLatestDefs(var, instructions, instr, visited, result);
    return result.toArray(new PyElement[result.size()]);
  }

  private static void getLatestDefs(final PyTargetExpression var,
                                    final Instruction[] instructions,
                                    final int instr,
                                    final boolean[] visited,
                                    final Collection<PyElement> result) {
    if (visited[instr]) return;
    visited[instr] = true;
    if (instructions[instr] instanceof WriteInstruction) {
      final WriteInstruction instruction = (WriteInstruction)instructions[instr];
      final String name = ((PyElement)instruction.getElement()).getName();
      if (Comparing.strEqual(name, var.getName())) {
        result.add((PyElement) instruction.getElement());
        return;
      }
    }
    for (Instruction instruction : instructions[instr].allPred()) {
      getLatestDefs(var, instructions, instruction.num(), visited, result);
    }
  }

  @NotNull
  public static PsiElement[] getPostRefs(ScopeOwner block, PyTargetExpression var, PyExpression anchor) {
    final ControlFlow controlFlow = block.getControlFlow();
    final Instruction[] instructions = controlFlow.getInstructions();
    final int instr = ControlFlowUtil.findInstructionNumberByElement(instructions, anchor);
    assert instr >= 0;
    final boolean[] visited = new boolean[instructions.length];
    final Collection<PyElement> result = new HashSet<PyElement>();
    for (Instruction instruction : instructions[instr].allSucc()) {
      getPostRefs(var, instructions, instruction.num(), visited, result);
    }
    return result.toArray(new PyElement[result.size()]);
  }

  private static void getPostRefs(PyTargetExpression var, Instruction[] instructions, int instr, boolean[] visited, Collection<PyElement> result) {
    if (visited[instr]) return;
    visited[instr] = true;
    if (instructions[instr] instanceof ReadInstruction) {
      final ReadInstruction instruction = (ReadInstruction)instructions[instr];
      final String name = ((PyElement)instruction.getElement()).getName();
      if (Comparing.strEqual(name, var.getName())) {
        result.add((PyElement)instruction.getElement());
      }
    }
    for (Instruction instruction : instructions[instr].allSucc()) {
      getPostRefs(var, instructions, instruction.num(), visited, result);
    }
  }

}
