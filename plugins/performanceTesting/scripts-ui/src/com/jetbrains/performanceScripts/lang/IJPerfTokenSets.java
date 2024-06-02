package com.jetbrains.performanceScripts.lang;

import com.intellij.psi.tree.TokenSet;
import com.jetbrains.performanceScripts.lang.psi.IJPerfElementTypes;

public final class IJPerfTokenSets {
  private IJPerfTokenSets() {
  }

  public static final TokenSet COMMENTS = TokenSet.create(IJPerfElementTypes.COMMENT);
}
