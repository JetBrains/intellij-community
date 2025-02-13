// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.highlighter;

import com.intellij.icons.AllIcons;
import com.intellij.lang.dtd.DTDLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.xml.XmlCoreBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class DTDFileType extends LanguageFileType {
  public static final DTDFileType INSTANCE = new DTDFileType();

  private DTDFileType() {
    super(DTDLanguage.INSTANCE);
  }

  @Override
  public @NotNull String getName() {
    return "DTD";
  }

  @Override
  public @NotNull String getDescription() {
    return XmlCoreBundle.message("filetype.dtd.description");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "dtd";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Dtd;
  }
}
