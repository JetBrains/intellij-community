// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface PyWithItem extends PyElement {
  PyWithItem[] EMPTY_ARRAY = new PyWithItem[0];

  @NotNull
  PyExpression getExpression();

  @Nullable
  PyExpression getTarget();
}
