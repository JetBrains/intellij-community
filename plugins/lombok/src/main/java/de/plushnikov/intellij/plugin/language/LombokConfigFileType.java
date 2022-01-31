package de.plushnikov.intellij.plugin.language;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.NlsContexts;
import de.plushnikov.intellij.plugin.LombokBundle;
import icons.LombokIcons;
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
    return LombokBundle.message("filetype.lombok.config.description");
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "config";
  }

  @Override
  public Icon getIcon() {
    return LombokIcons.Config;
  }
}
