package com.intellij.psi.controlFlow;

public class ConditionalThrowToInstruction extends ConditionalBranchingInstruction {

  public ConditionalThrowToInstruction(int offset) {
    super(offset);
  }

  public String toString() {
    return "COND_THROW_TO " + offset;
  }

  public void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitConditionalThrowToInstruction(this, offset, nextOffset);
  }
}
