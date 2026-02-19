// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performanceScripts.lang;

import com.intellij.psi.tree.TokenSet;
import com.jetbrains.performanceScripts.lang.psi.IJPerfElementTypes;

public final class IJPerfTokenSets {
  private IJPerfTokenSets() {
  }

  public static final TokenSet COMMENTS = TokenSet.create(IJPerfElementTypes.COMMENT);
}
