// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performanceScripts.lang.psi;

import com.intellij.psi.tree.IElementType;
import com.jetbrains.performanceScripts.lang.IJPerfLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class IJPerfTokenType extends IElementType {

  public IJPerfTokenType(@NotNull @NonNls String debugName) {
    super(debugName, IJPerfLanguage.INSTANCE);
  }

  @Override
  public String toString() {
    return "PerformanceTestTokenType." + super.toString();
  }
}
