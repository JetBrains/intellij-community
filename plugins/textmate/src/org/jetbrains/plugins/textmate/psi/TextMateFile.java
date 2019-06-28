package org.jetbrains.plugins.textmate.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.language.TextMateFileType;
import org.jetbrains.plugins.textmate.language.TextMateLanguage;

public class TextMateFile extends PsiFileBase {
  public TextMateFile(FileViewProvider provider) {
    super(provider, TextMateLanguage.LANGUAGE);
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return TextMateFileType.INSTANCE;
  }
}
