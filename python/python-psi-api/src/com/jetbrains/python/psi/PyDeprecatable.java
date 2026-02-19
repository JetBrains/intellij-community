// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

public interface PyDeprecatable {
  default @Nullable String getDeprecationMessage() { return null; }
}
