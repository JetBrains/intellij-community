// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.jira.jql;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.tasks.TaskBundle;
import icons.TasksCoreIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Mikhail Golubev
 */
public final class JqlFileType extends LanguageFileType {
  public static final LanguageFileType INSTANCE = new JqlFileType();
  public static final String DEFAULT_EXTENSION = "jql";

  private JqlFileType() {
    super(JqlLanguage.INSTANCE);
  }

  @Override
  public @NotNull String getName() {
    return "JQL";
  }

  @Override
  public @NotNull String getDescription() {
    return TaskBundle.message("filetype.jira.query.description");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return TasksCoreIcons.Jira;
  }
}
