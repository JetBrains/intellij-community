package com.intellij.bash.psi;

import com.intellij.bash.ShLanguage;
import com.intellij.psi.tree.IElementType;

public class ShCompositeElementType extends IElementType {
  public ShCompositeElementType(String debug) {
    super(debug, ShLanguage.INSTANCE);
  }
}
