package com.intellij.bash;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class BashFileType extends LanguageFileType {
  public static final BashFileType INSTANCE = new BashFileType();

  private BashFileType() {
    super(BashLanguage.INSTANCE);
  }

  @NotNull
  public String getName() {
    return "Bash";
  }

  @NotNull
  public String getDescription() {
    return "Bash";
  }

  @NotNull
  public String getDefaultExtension() {
    return "sh";
  }

  public Icon getIcon() {
    return BashIcons.FILE_TYPE;
  }
}
