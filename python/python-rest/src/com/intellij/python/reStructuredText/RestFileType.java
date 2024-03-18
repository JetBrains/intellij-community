// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * User : catherine
 *
 * file type for restructured text files
 */
public final class RestFileType extends LanguageFileType {
  public static final RestFileType INSTANCE = new RestFileType();
  @NonNls public static final String DEFAULT_EXTENSION = "rst";

  private RestFileType() {
    super(RestLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public String getName() {
    return "ReST";
  }

  @Override
  @NotNull
  public String getDescription() {
    return RestBundle.message("restructured.text");
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return PythonReStructuredTextIcons.Rst;
  }
}

