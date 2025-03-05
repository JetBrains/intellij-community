// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.highlighter;

import com.intellij.icons.AllIcons;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.fileTypes.OSFileIdeAssociation;
import com.intellij.xml.XmlCoreBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class XmlFileType extends XmlLikeFileType implements DomSupportEnabled, OSFileIdeAssociation {
  public static final XmlFileType INSTANCE = new XmlFileType();

  public static final String DEFAULT_EXTENSION = "xml";
  public static final String DOT_DEFAULT_EXTENSION = "." + DEFAULT_EXTENSION;

  private XmlFileType() {
    super(XMLLanguage.INSTANCE);
  }

  @Override
  public @NotNull String getName() {
    return "XML";
  }

  @Override
  public @NotNull String getDescription() {
    return XmlCoreBundle.message("filetype.xml.description");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Xml;
  }

  @Override
  public @NotNull ExtensionMode getExtensionMode() {
    return ExtensionMode.Selected;
  }
}
