package com.jetbrains.performancePlugin.lang;

import com.intellij.psi.tree.TokenSet;
import com.jetbrains.performancePlugin.lang.psi.IJPerfElementTypes;

public final class IJPerfTokenSets {
  private IJPerfTokenSets() {
  }

  public static final TokenSet COMMENTS = TokenSet.create(IJPerfElementTypes.COMMENT);
}
