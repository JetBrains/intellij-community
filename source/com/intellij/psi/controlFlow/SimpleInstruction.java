/**
 * @author cdr
 */
package com.intellij.psi.controlFlow;

import com.intellij.openapi.diagnostic.Logger;

public abstract class SimpleInstruction extends InstructionBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.controlFlow.SimpleInstruction");

  public int nNext() { return 1; }

  public int getNext(int index, int no) {
    LOG.assertTrue(no == 0);
    return index + 1;
  }

  public void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitSimpleInstruction(this, offset, nextOffset);
  }
}