package com.intellij.sh.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.sh.ShLanguage;

public class ShCompositeElementType extends IElementType {
  public ShCompositeElementType(String debug) {
    super(debug, ShLanguage.INSTANCE);
  }
}
