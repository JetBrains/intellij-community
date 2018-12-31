package com.intellij.bash.psi;

import com.intellij.bash.BashLanguage;
import com.intellij.psi.tree.IElementType;

public class BashCompositeElementType extends IElementType {
  public BashCompositeElementType(String debug) {
    super(debug, BashLanguage.INSTANCE);
  }
}
