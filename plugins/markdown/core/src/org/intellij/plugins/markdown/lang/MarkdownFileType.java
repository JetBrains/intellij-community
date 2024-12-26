// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.NlsSafe;
import org.intellij.plugins.markdown.MarkdownIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class MarkdownFileType extends LanguageFileType {
  public static final MarkdownFileType INSTANCE = new MarkdownFileType();

  private static final @NlsSafe String MARKDOWN_DESCRIPTION = "Markdown";

  private MarkdownFileType() {
    super(MarkdownLanguage.INSTANCE);
  }

  @Override
  public @NotNull String getName() {
    return "Markdown";
  }

  @Override
  public @NotNull String getDescription() {
    return MARKDOWN_DESCRIPTION;
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "md";
  }

  @Override
  public Icon getIcon() {
    return MarkdownIcons.MarkdownPlugin;
  }
}
