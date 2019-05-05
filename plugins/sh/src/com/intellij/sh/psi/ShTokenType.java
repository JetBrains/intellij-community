package com.intellij.sh.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.sh.ShLanguage;
import org.jetbrains.annotations.NotNull;

public class ShTokenType extends IElementType {
  public ShTokenType(@NotNull String debugName) {
    super(debugName, ShLanguage.INSTANCE);
  }
}
