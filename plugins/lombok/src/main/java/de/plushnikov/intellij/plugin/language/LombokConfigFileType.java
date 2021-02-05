package de.plushnikov.intellij.plugin.language;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.NlsContexts;
import de.plushnikov.intellij.plugin.LombokBundle;
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
  @NlsContexts.Label
  public String getDescription() {
    return LombokBundle.message("label.lombok.config.file");
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
