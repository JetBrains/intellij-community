package com.jetbrains.python.codeInsight.controlflow;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.controlflow.ControlFlowBuilder;
import com.intellij.codeInsight.controlflow.Instruction;
import com.jetbrains.python.psi.PyReferenceExpression;

import java.util.List;

/**
 * @author traff
 */
public class InstructionBuilder {
  private InstructionBuilder() {
  }

  public static List<Instruction> buildInstructions(ControlFlowBuilder builder, List<PyTypeAssertionEvaluator.Assertion> assertions) {
    List<Instruction> result = Lists.newArrayList();
    for (PyTypeAssertionEvaluator.Assertion def: assertions) {
      final PyReferenceExpression e = def.getElement();
      result.add(ReadWriteInstruction.assertType(builder, e, e.getName(), def.getTypeEvalFunction()));
    }
    return result;
  }

  public static void addAssertInstructions(ControlFlowBuilder builder, PyTypeAssertionEvaluator assertionEvaluator) {
    for (Instruction instr : buildInstructions(builder, assertionEvaluator.getDefinitions())) {
      builder.addNode(instr);
    }
  }
}
