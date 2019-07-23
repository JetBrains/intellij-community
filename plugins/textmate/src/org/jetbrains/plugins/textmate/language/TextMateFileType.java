package org.jetbrains.plugins.textmate.language;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.TextMateService;

import javax.swing.*;

/**
 * User: zolotov
 * <p/>
 * FileType corresponding to any language that supported via TextMate bundle.
 */
public class TextMateFileType extends LanguageFileType implements PlainTextLikeFileType, FileTypeIdentifiableByVirtualFile {
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

  @Override
  public boolean isMyFileType(@NotNull VirtualFile file) {
    CharSequence fileName = file.getNameSequence();
    FileType originalFileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    if (!isTypeShouldBeReplacedByTextMateType(originalFileType)) {
      return false;
    }
    return TextMateService.getInstance().getLanguageDescriptorByFileName(fileName) != null;
  }

  private static boolean isTypeShouldBeReplacedByTextMateType(@Nullable FileType registeredType) {
    return registeredType == UnknownFileType.INSTANCE
           || registeredType == INSTANCE
           || registeredType == PlainTextFileType.INSTANCE;
  }
}
