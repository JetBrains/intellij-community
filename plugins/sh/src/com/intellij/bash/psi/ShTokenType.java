package com.intellij.bash.psi;

import com.intellij.bash.ShLanguage;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class ShTokenType extends IElementType {
  public ShTokenType(@NotNull String debugName) {
    super(debugName, ShLanguage.INSTANCE);
  }
}
