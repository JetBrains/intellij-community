package com.jetbrains.python.codeInsight.controlflow;

import com.jetbrains.python.psi.PyElement;

/**
 * @author oleg
 */
public interface ControlFlowOwner extends PyElement {
  ControlFlow getControlFlow();
}
