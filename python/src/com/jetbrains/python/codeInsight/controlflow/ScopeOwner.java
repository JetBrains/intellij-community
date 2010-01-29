package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.psi.PyElement;

/**
 * @author oleg
 */
public interface ScopeOwner extends PyElement {
  ControlFlow getControlFlow();
  Scope getScope();
}
