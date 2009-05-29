package com.intellij.psi.controlFlow;

import com.intellij.openapi.diagnostic.Logger;


public class ThrowToInstruction extends BranchingInstruction {

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.controlFlow.ThrowToInstruction");

  public ThrowToInstruction(int offset) {
    super(offset, Role.END);
  }

  public String toString() {
    return "THROW_TO " + offset;
  }

  public int nNext() { return 1; }

  public int getNext(int index, int no) {
    LOG.assertTrue(no == 0);
    return offset;
  }

  public void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitThrowToInstruction(this, offset, nextOffset);
  }
}
