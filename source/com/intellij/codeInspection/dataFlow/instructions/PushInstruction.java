/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 7, 2002
 * Time: 1:25:41 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.value.DfaValue;

public class PushInstruction extends Instruction {
  private final DfaValue myValue;

  public PushInstruction(DfaValue myValue) {
    this.myValue = myValue;
  }

  public DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState memState) {
    memState.push(myValue);
    return new DfaInstructionState[]{new DfaInstructionState(runner.getInstruction(getIndex() + 1),memState)};
  }

  public String toString() {
    return "PUSH " + myValue;
  }
}
