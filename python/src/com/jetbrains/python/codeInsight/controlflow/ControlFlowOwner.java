package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.jetbrains.python.psi.PyElement;

/**
 * @author oleg
 */
public interface ControlFlowOwner extends PyElement {
  ControlFlow getControlFlow();
}
