// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.stubs.PySingleStarParameterStub;
import org.jetbrains.annotations.NonNls;

/**
 * Represents a single star (keyword-only parameter delimiter) appearing in the
 * parameter list of a Py3K function.
 */
public interface PySingleStarParameter extends PyParameter, StubBasedPsiElement<PySingleStarParameterStub> {
  @NonNls String TEXT = "*";
}
