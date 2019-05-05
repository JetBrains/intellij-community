package com.intellij.bash.psi;

import com.intellij.bash.ShLanguage;
import com.intellij.psi.tree.IFileElementType;

public class ShFileElementType extends IFileElementType {
  public static final ShFileElementType INSTANCE = new ShFileElementType();

  public ShFileElementType() {
    super("BASH_FILE", ShLanguage.INSTANCE);
  }
}
