// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.highlighter;

import com.intellij.icons.AllIcons;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.xml.psi.XmlPsiBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class XHtmlFileType extends HtmlFileType {
  public static final XHtmlFileType INSTANCE = new XHtmlFileType();

  private XHtmlFileType() {
    super(XHTMLLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public String getName() {
    return "XHTML";
  }

  @Override
  @NotNull
  public String getDescription() {
    return XmlPsiBundle.message("filetype.description.xhtml");
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return "xhtml";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Xhtml;
  }
}
