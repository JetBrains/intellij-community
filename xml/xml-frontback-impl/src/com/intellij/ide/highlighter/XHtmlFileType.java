// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.highlighter;

import com.intellij.icons.AllIcons;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.xml.XmlCoreBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class XHtmlFileType extends HtmlFileType {
  public static final XHtmlFileType INSTANCE = new XHtmlFileType();

  private XHtmlFileType() {
    super(XHTMLLanguage.INSTANCE);
  }

  @Override
  public @NotNull String getName() {
    return "XHTML";
  }

  @Override
  public @NotNull String getDescription() {
    return XmlCoreBundle.message("filetype.xhtml.description");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "xhtml";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Xhtml;
  }
}
