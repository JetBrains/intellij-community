package com.intellij.psi.controlFlow;

abstract class InstructionClientVisitor<T> extends ControlFlowInstructionVisitor {
  public abstract T getResult();

  protected final boolean isLeaf(int offset) {
    return offset == processedInstructions.length;
  }

  protected boolean[] processedInstructions;
}