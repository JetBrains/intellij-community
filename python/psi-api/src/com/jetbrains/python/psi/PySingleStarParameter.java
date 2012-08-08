package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.stubs.PySingleStarParameterStub;

/**
 * Represents a single star (keyword-only parameter delimiter) appearing in the
 * parameter list of a Py3K function.
 *
 * @author yole
 */
public interface PySingleStarParameter extends PyParameter, StubBasedPsiElement<PySingleStarParameterStub> {
}
