package com.intellij.sh.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.sh.ShFileType;
import com.intellij.sh.ShLanguage;
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