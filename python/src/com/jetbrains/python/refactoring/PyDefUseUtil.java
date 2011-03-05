package com.jetbrains.python.refactoring;

import com.google.common.collect.Sets;
import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;

/**
 * @author Dennis.Ushakov
 */
public class PyDefUseUtil {
  private PyDefUseUtil() {
  }

  @NotNull
  public static PyElement[] getLatestDefs(ScopeOwner block, PyElement var, PsiElement anchor) {
    final ControlFlow controlFlow = ControlFlowCache.getControlFlow(block);
    final Instruction[] instructions = controlFlow.getInstructions();
    final int instr = ControlFlowUtil.findInstructionNumberByElement(instructions, anchor);
    if (instr < 0) {
      throw new InstructionNotFoundException();
    }
    final boolean[] visited = new boolean[instructions.length];
    final Collection<PyElement> result = new HashSet<PyElement>();
    getLatestDefs(var, instructions, instr, visited, result);
    return result.toArray(new PyElement[result.size()]);
  }

  private static void getLatestDefs(final PyElement var,
                                    final Instruction[] instructions,
                                    final int instr,
                                    final boolean[] visited,
                                    final Collection<PyElement> result) {
    if (visited[instr]) return;
    visited[instr] = true;
    if (instructions[instr] instanceof ReadWriteInstruction) {
      final ReadWriteInstruction instruction = (ReadWriteInstruction)instructions[instr];
      final PsiElement element = instruction.getElement();
      String name = elementName(element);
      final ReadWriteInstruction.ACCESS access = instruction.getAccess();
      if (access == ReadWriteInstruction.ACCESS.WRITETYPE) {
        name = instruction.getName();
      }
      if (access.isWriteAccess() && Comparing.strEqual(name, var.getName())) {
        result.add((PyElement)element);
        if (access != ReadWriteInstruction.ACCESS.WRITETYPE) {
          return;
        }
      }
    }
    for (Instruction instruction : instructions[instr].allPred()) {
      getLatestDefs(var, instructions, instruction.num(), visited, result);
    }
  }

  @Nullable
  private static String elementName(PsiElement element) {
    return element instanceof PyElement ? ((PyElement)element).getName() : null;
  }

  @NotNull
  public static PsiElement[] getPostRefs(ScopeOwner block, PyTargetExpression var, PyExpression anchor) {
    final ControlFlow controlFlow = ControlFlowCache.getControlFlow(block);
    final Instruction[] instructions = controlFlow.getInstructions();
    final int instr = ControlFlowUtil.findInstructionNumberByElement(instructions, anchor);
    assert instr >= 0;
    final boolean[] visited = new boolean[instructions.length];
    final Collection<PyElement> result = Sets.newHashSet();
    for (Instruction instruction : instructions[instr].allSucc()) {
      getPostRefs(var, instructions, instruction.num(), visited, result);
    }
    return result.toArray(new PyElement[result.size()]);
  }

  private static void getPostRefs(PyTargetExpression var,
                                  Instruction[] instructions,
                                  int instr,
                                  boolean[] visited,
                                  Collection<PyElement> result) {
    if (visited[instr]) return;
    visited[instr] = true;
    if (instructions[instr] instanceof ReadWriteInstruction) {
      final ReadWriteInstruction instruction = (ReadWriteInstruction)instructions[instr];
      final PsiElement element = instruction.getElement();
      String name = elementName(element);
      if (Comparing.strEqual(name, var.getName())) {
        final ReadWriteInstruction.ACCESS access = instruction.getAccess();
        if (access.isWriteAccess()) {
          return;
        }
        result.add((PyElement)instruction.getElement());
      }
    }
    for (Instruction instruction : instructions[instr].allSucc()) {
      getPostRefs(var, instructions, instruction.num(), visited, result);
    }
  }

  public static class InstructionNotFoundException extends RuntimeException {
  }
}
