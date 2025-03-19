package de.plushnikov.intellij.plugin.language;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.NlsContexts;
import de.plushnikov.intellij.plugin.LombokBundle;
import icons.LombokIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class LombokConfigFileType extends LanguageFileType {
  public static final LombokConfigFileType INSTANCE = new LombokConfigFileType();

  private LombokConfigFileType() {
    super(LombokConfigLanguage.INSTANCE);
  }

  @Override
  public @NotNull String getName() {
    return "LOMBOK_CONFIG";
  }

  @Override
  public @NotNull @NlsContexts.Label String getDescription() {
    return LombokBundle.message("filetype.lombok.config.description");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "config";
  }

  @Override
  public Icon getIcon() {
    return LombokIcons.Config;
  }
}
