// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.highlighter;

import com.intellij.icons.AllIcons;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.xml.psi.XmlPsiBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class XmlFileType extends XmlLikeFileType implements DomSupportEnabled {
  public static final XmlFileType INSTANCE = new XmlFileType();
  @NonNls public static final String DEFAULT_EXTENSION = "xml";
  @NonNls public static final String DOT_DEFAULT_EXTENSION = "."+DEFAULT_EXTENSION;

  private XmlFileType() {
    super(XMLLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public String getName() {
    return "XML";
  }

  @Override
  @NotNull
  public String getDescription() {
    return XmlPsiBundle.message("filetype.description.xml");
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Xml;
  }
}
