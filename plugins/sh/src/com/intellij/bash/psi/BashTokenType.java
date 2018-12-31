package com.intellij.bash.psi;

import com.intellij.bash.BashLanguage;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class BashTokenType extends IElementType {
  public BashTokenType(@NotNull String debugName) {
    super(debugName, BashLanguage.INSTANCE);
  }
}
