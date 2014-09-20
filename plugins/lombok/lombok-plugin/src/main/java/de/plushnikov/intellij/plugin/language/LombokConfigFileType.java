package de.plushnikov.intellij.plugin.language;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class LombokConfigFileType extends LanguageFileType {
  public static final LombokConfigFileType INSTANCE = new LombokConfigFileType();

  private LombokConfigFileType() {
    super(LombokConfigLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public String getName() {
    return "Lombok config file";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Lombok config file";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "config";
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return LombokConfigIcons.FILE;
  }
}
