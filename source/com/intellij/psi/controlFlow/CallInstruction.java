package com.intellij.psi.controlFlow;



public class CallInstruction extends GoToInstruction {
  public final ControlFlowStack stack;
  public int procBegin;
  public int procEnd;

  public CallInstruction(int procBegin, int procEnd, ControlFlowStack stack) {
    super(procBegin);
    this.stack = stack;
    this.procBegin = procBegin;
    this.procEnd = procEnd;
  }

  public String toString() {
    return "CALL " + offset ;
  }

  public void execute(int returnOffset) {
    stack.push(returnOffset, this);
  }

  public void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitCallInstruction(this, offset, nextOffset);
  }
}
