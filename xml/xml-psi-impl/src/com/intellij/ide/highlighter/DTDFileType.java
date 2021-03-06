// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.highlighter;

import com.intellij.icons.AllIcons;
import com.intellij.lang.dtd.DTDLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.xml.psi.XmlPsiBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class DTDFileType extends LanguageFileType {
  public static final DTDFileType INSTANCE = new DTDFileType();

  private DTDFileType() {
    super(DTDLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public String getName() {
    return "DTD";
  }

  @Override
  @NotNull
  public String getDescription() {
    return XmlPsiBundle.message("filetype.description.dtd");
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return "dtd";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Dtd;
  }
}
