package org.jetbrains.yaml;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class YAMLFileType extends LanguageFileType {
  public static final YAMLFileType YML = new YAMLFileType();
  @NonNls public static final String DEFAULT_EXTENSION = "yml";
  @NonNls private static final String NAME = "YAML";
  @NonNls private static final String DESCRIPTION = YAMLBundle.message("filetype.description.yaml");

  private YAMLFileType() {
    super(YAMLLanguage.INSTANCE);
  }

  @NotNull
  public String getName() {
    return NAME;
  }

  @NotNull
  public String getDescription() {
    return DESCRIPTION;
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Nullable
  public Icon getIcon() {
    return AllIcons.Nodes.DataTables;
  }
}

