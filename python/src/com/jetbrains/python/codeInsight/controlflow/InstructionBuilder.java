package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.codeInsight.controlflow.ControlFlowBuilder;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.util.containers.CollectionFactory;

import java.util.List;

/**
 * @author traff
 */
public class InstructionBuilder {
  public static List<Instruction> buildInstructions(ControlFlowBuilder builder, List<PyConditionEvaluator.Definition> definitions) {
    List<Instruction> result = CollectionFactory.arrayList();
    for (PyConditionEvaluator.Definition def: definitions) {
      processDef(builder, def, result);
    }
    return result;
  }

  private static void processDef(ControlFlowBuilder builder, PyConditionEvaluator.Definition def, List<Instruction> result) {
    result.add(ReadWriteInstruction.writeType(builder, def.getElement(), def.getName()));            
  }
}
