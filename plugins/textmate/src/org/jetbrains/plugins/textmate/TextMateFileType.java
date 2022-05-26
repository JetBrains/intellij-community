package org.jetbrains.plugins.textmate;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: zolotov
 * <p/>
 * FileType corresponding to any language that supported via TextMate bundle.
 */
public final class TextMateFileType extends LanguageFileType implements FileTypeIdentifiableByVirtualFile, PlainTextLikeFileType {
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
    return TextMateBundle.message("filetype.textmate.description");
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Text;
  }

  @Override
  public boolean isMyFileType(@NotNull VirtualFile file) {
    if (file.isDirectory() || !(file instanceof LightVirtualFile)) {
      return false;
    }
    CharSequence fileName = file.getNameSequence();
    FileType originalFileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    return isTypeShouldBeReplacedByTextMateType(originalFileType) &&
           TextMateService.getInstance().getLanguageDescriptorByFileName(fileName) != null;
  }

  private static boolean isTypeShouldBeReplacedByTextMateType(@Nullable FileType registeredType) {
    return registeredType == UnknownFileType.INSTANCE
           || registeredType == INSTANCE
           || registeredType == PlainTextFileType.INSTANCE;
  }

  private static class TextMateFileDetector implements FileTypeRegistry.FileTypeDetector {
    @Override
    public @Nullable FileType detect(@NotNull VirtualFile file, @NotNull ByteSequence firstBytes, @Nullable CharSequence firstCharsIfText) {
      if (file.isDirectory()) {
        return null;
      }
      // additional precaution: sometimes TextMate erroneously thinks the file is his, when in reality it's a huge binary
      if (firstCharsIfText == null) {
        return null;
      }
      CharSequence fileName = file.getNameSequence();
      FileType originalFileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
      if (!isTypeShouldBeReplacedByTextMateType(originalFileType)) {
        return null;
      }
      boolean textMateRecognizesMe = false;
      try {
        textMateRecognizesMe = TextMateService.getInstance().getLanguageDescriptorByFileName(fileName) != null;
      }
      catch (ProcessCanceledException ignored) {
      }
      if (!textMateRecognizesMe) {
        return null;
      }
      return INSTANCE;
    }

    @Override
    public int getDesiredContentPrefixLength() {
      return 0;
    }
  }
}
