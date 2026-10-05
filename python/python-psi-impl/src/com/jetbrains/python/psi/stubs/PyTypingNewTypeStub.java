// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.stubs;

import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PyTypingNewTypeStub extends CustomTargetExpressionStub {

  @NotNull
  String getName();

  @NotNull
  String getClassType();

  /**
   * Returns the callee text of the {@code NewType(...)} call when the stub builder cannot prove that the callee is
   * {@code typing.NewType}, for example, when another module re-exports it. Returns {@code null} otherwise.
   */
  @Nullable
  String getUnresolvedCalleeName();
}
