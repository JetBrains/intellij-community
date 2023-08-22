// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.intellij.plugins.markdown.MarkdownBundle;
import org.intellij.plugins.markdown.MarkdownIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class MarkdownFileType extends LanguageFileType {
  public static final MarkdownFileType INSTANCE = new MarkdownFileType();

  private MarkdownFileType() {
    super(MarkdownLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public String getName() {
    return "Markdown";
  }

  @NotNull
  @Override
  public String getDescription() {
    return MarkdownBundle.message("filetype.markdown.description");
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "md";
  }

  @Override
  public Icon getIcon() {
    return MarkdownIcons.MarkdownPlugin;
  }
}
