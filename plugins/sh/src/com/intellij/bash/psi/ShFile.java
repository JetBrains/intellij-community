package com.intellij.bash.psi;

import com.intellij.bash.ShFileType;
import com.intellij.bash.ShLanguage;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public class ShFile extends PsiFileBase {
  public ShFile(@NotNull FileViewProvider viewProvider) {
    super(viewProvider, ShLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return ShFileType.INSTANCE;
  }
}