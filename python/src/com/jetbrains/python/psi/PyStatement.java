package com.jetbrains.python.psi;

/**
 * Everything that may be an element of a statement list.
 */
public interface PyStatement extends PyElement {
    PyStatement[] EMPTY_ARRAY = new PyStatement[0];
}
