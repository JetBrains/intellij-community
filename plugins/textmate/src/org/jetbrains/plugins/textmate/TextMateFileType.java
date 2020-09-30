package org.jetbrains.plugins.textmate;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: zolotov
 * <p/>
 * FileType corresponding to any language that supported via TextMate bundle.
 */
public final class TextMateFileType extends LanguageFileType implements PlainTextLikeFileType, FileTypeIdentifiableByVirtualFile {
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
    return TextMateBundle.message("textmate.filetype.description");
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
    if (file.isDirectory()) {
      return false;
    }
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
