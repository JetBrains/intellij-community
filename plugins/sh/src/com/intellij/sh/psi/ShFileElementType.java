package com.intellij.sh.psi;

import com.intellij.psi.tree.IFileElementType;
import com.intellij.sh.ShLanguage;

public class ShFileElementType extends IFileElementType {
  public static final ShFileElementType INSTANCE = new ShFileElementType();

  public ShFileElementType() {
    super("BASH_FILE", ShLanguage.INSTANCE);
  }
}
