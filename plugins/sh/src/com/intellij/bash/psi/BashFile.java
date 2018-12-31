package com.intellij.bash.psi;

import com.intellij.bash.BashFileType;
import com.intellij.bash.BashLanguage;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public class BashFile extends PsiFileBase {
  public BashFile(@NotNull FileViewProvider viewProvider) {
    super(viewProvider, BashLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return BashFileType.INSTANCE;
  }
}