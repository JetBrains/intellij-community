package com.intellij.psi.controlFlow;

import com.intellij.openapi.diagnostic.Logger;


public class ReturnInstruction extends GoToInstruction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.controlFlow.ReturnInstruction");

  public final ControlFlowStack stack;
  private CallInstruction myCallInstruction;

  public ReturnInstruction(int offset, ControlFlowStack stack, CallInstruction callInstruction) {
    super(offset, ControlFlow.JUMP_ROLE_GOTO_END, false);
    this.stack = stack;
    myCallInstruction = callInstruction;
  }

  public String toString() {
    return "RETURN FROM " + getProcBegin() + (offset == 0 ? "" : " TO "+offset);
  }

  public int execute(boolean pushBack) {
    int jumpTo = -1;
    if (stack.size() != 0) {
      jumpTo = stack.pop(pushBack);
    }
    if (offset != 0) {
      jumpTo = offset;
    }
    return jumpTo;
  }

  public int[] getPossibleReturnOffsets() {
    return offset == 0 ?
        new int[]{
          getProcBegin() - 5, // call normal
          getProcBegin() - 3, // call return
          getProcBegin() - 1, // call throw
        }
        :
        new int[]{
          offset,    // exit from middle of the finally
        };

  }

  public int getProcBegin() {
    return myCallInstruction.procBegin;
  }

  public int getProcEnd() {
    return myCallInstruction.procEnd;
  }

  public void setCallInstruction(CallInstruction callInstruction) {
    myCallInstruction = callInstruction;
  }


  public int nNext() { return offset == 0 ? 3 : 1; }

  public int getNext(int index, int no) {
    if (offset == 0)
      switch (no) {
        case 0: return getProcBegin() - 5; // call normal
        case 1: return getProcBegin() - 3; // call return
        case 2: return getProcBegin() - 1; // call throw
        default:
          LOG.assertTrue (false);
          return -1;
      }
    else
      switch (no) {
        case 0: return offset; // call normal
        default:
          LOG.assertTrue (false);
          return -1;
      }
  }

  public void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitReturnInstruction(this, offset, nextOffset);
  }
}
