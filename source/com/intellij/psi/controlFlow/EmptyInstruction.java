package com.intellij.psi.controlFlow;

public class EmptyInstruction extends SimpleInstruction {
  public String toString() {
    return "EMPTY";
  }

  public void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitEmptyInstruction(this, offset, nextOffset);
  }
}
