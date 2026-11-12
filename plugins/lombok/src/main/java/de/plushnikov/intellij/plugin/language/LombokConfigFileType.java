package de.plushnikov.intellij.plugin.language;

import com.intellij.lombok.shared.LombokBundle;
import com.intellij.lombok.shared.icons.LombokSharedIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

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
    return LombokSharedIcons.Config;
  }
}
