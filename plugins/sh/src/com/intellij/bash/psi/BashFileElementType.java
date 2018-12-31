package com.intellij.bash.psi;

import com.intellij.bash.BashLanguage;
import com.intellij.psi.tree.IFileElementType;

public class BashFileElementType extends IFileElementType {
  public static final BashFileElementType INSTANCE = new BashFileElementType();

  public BashFileElementType() {
    super("BASH_FILE", BashLanguage.INSTANCE);
  }
}
