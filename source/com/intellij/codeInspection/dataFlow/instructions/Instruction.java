/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 26, 2002
 * Time: 10:46:40 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;

import java.util.ArrayList;

public abstract class Instruction {
  private int myIndex;
  private final ArrayList myProcessedStates;

  protected Instruction() {
    myProcessedStates = new ArrayList();
  }

  public abstract DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState dfaBeforeMemoryState);

  public boolean isMemoryStateProcessed(DfaMemoryState dfaMemState) {
    for (int i = 0; i < myProcessedStates.size(); i++) {
      DfaMemoryState state = (DfaMemoryState) myProcessedStates.get(i);
      if (dfaMemState.equals(state)) return true;
    }

    return false;
  }

  public void setMemoryStateProcessed(DfaMemoryState dfaMemState) {
    myProcessedStates.add(dfaMemState);
  }

  public void setIndex(int index) {
    myIndex = index;
  }

  public int getIndex() {
    return myIndex;
  }
}
