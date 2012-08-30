package com.jetbrains.rest;

import com.intellij.openapi.fileTypes.LanguageFileType;
import icons.RestIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User : catherine
 *
 * file type for restructured text files
 */
public class RestFileType extends LanguageFileType {
  public static final RestFileType INSTANCE = new RestFileType();
  @NonNls public static final String DEFAULT_EXTENSION = "rst";
  @NonNls private static final String NAME = "ReST";
  @NonNls private static final String DESCRIPTION = "reStructuredText files";

  private RestFileType() {
    super(RestLanguage.INSTANCE);
  }

  @NotNull
  public String getName() {
    return NAME;
  }

  @NotNull
  public String getDescription() {
    return DESCRIPTION;
  }

  @NotNull
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Nullable
  public Icon getIcon() {
    return RestIcons.Rst;
  }
}

