// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.sh.ShLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ShTokenType extends IElementType {
  public ShTokenType(@NonNls @NotNull String debugName) {
    super(debugName, ShLanguage.INSTANCE);
  }
}
