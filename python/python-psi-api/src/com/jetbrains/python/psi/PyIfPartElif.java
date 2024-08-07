// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.ast.PyAstIfPartElif;
import com.jetbrains.python.psi.stubs.PyIfPartElifStub;

public interface PyIfPartElif extends PyAstIfPartElif, PyIfPart, StubBasedPsiElement<PyIfPartElifStub> {
}
