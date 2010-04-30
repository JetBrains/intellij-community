package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.stubs.PyImportStatementStub;

/**
 * @author yole
 */
public interface PyImportStatement extends PyImportStatementBase, StubBasedPsiElement<PyImportStatementStub> {
}
