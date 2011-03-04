package com.jetbrains.python.codeInsight.controlflow;

import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.psi.PyElement;

/**
 * Marker interface for Python elements which define a scope.
 *
 * @see ControlFlowCache
 * @author oleg
 */
public interface ScopeOwner extends PyElement {
}
