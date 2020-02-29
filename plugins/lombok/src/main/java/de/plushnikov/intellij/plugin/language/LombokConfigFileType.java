package de.plushnikov.intellij.plugin.language;

import com.intellij.openapi.fileTypes.LanguageFileType;
import de.plushnikov.intellij.plugin.icon.LombokIcons;
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
    return "LOMBOK_CONFIG";
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
    return LombokIcons.CONFIG_FILE_ICON;
  }
}
