package com.jetbrains.python.buildout.config;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.jetbrains.python.buildout.BuildoutFacetType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author traff
 */
public class BuildoutCfgFileType extends LanguageFileType {
  public static final BuildoutCfgFileType INSTANCE = new BuildoutCfgFileType();
  @NonNls public static final String DEFAULT_EXTENSION = "cfg";
  @NonNls private static final String NAME = "BuildoutCfg";
  @NonNls private static final String DESCRIPTION = "Buildout config files";

  private BuildoutCfgFileType() {
    super(BuildoutCfgLanguage.INSTANCE);
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
    return BuildoutFacetType.BUILDOUT_ICON;
  }
}

