// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.jetbrains.python.psi.PyNamedParameter;
import org.jetbrains.annotations.Nullable;

public interface PyNamedParameterStub extends NamedStub<PyNamedParameter>, PyAnnotationOwnerStub, PyTypeCommentOwnerStub {
  boolean isPositionalContainer();
  boolean isKeywordContainer();

  @Nullable
  default String getDefaultValueText() {
    return null;
  }
}