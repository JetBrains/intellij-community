// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi;

import com.intellij.psi.tree.IFileElementType;
import com.intellij.sh.ShLanguage;

public class ShFileElementType extends IFileElementType {
  public static final ShFileElementType INSTANCE = new ShFileElementType();

  public ShFileElementType() {
    super("SHELL_SCRIPT", ShLanguage.INSTANCE);
  }
}
