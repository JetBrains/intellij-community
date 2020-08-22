// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class ShFileType extends LanguageFileType {
  public static final ShFileType INSTANCE = new ShFileType();

  private ShFileType() {
    super(ShLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public String getName() {
    return "Shell Script";
  }

  @Override
  @NotNull
  public String getDescription() {
    return ShBundle.message("sh.title.case");
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return "sh";
  }

  @Override
  public Icon getIcon() {
    return ShIcons.FILE_TYPE;
  }
}
