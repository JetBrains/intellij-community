// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.stubs;

import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStub;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
@ApiStatus.Internal
public interface PyTypingAliasStub extends CustomTargetExpressionStub {
  @NotNull
  String getText();
}
