package com.jetbrains.python.pyi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public final class PyiUtilCore {
  public static boolean isInsideStub(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    return file != null && file.getFileType() == PyiFileType.INSTANCE;
  }
}
