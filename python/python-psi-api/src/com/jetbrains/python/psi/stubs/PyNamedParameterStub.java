// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.jetbrains.python.psi.PyNamedParameter;
import org.jetbrains.annotations.Nullable;

public interface PyNamedParameterStub extends NamedStub<PyNamedParameter>, PyAnnotationOwnerStub, PyTypeCommentOwnerStub {
  boolean isPositionalContainer();
  boolean isKeywordContainer();

  default @Nullable String getDefaultValueText() {
    return null;
  }
}