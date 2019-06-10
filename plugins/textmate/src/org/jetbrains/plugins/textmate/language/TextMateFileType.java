package org.jetbrains.plugins.textmate.language;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextLikeFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: zolotov
 * <p/>
 * FileType corresponding to any language that supported via TextMate bundle.
 */
public class TextMateFileType extends LanguageFileType implements PlainTextLikeFileType {
  public static final TextMateFileType INSTANCE = new TextMateFileType();

  private TextMateFileType() {
    super(TextMateLanguage.LANGUAGE);
  }

  @NotNull
  @Override
  public String getName() {
    return "textmate";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Files supported via TextMate bundles";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "";
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Text;
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }
}
