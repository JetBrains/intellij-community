package com.jetbrains.edu.coursecreator;

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class CCHighlightErrorFilter extends HighlightErrorFilter {
  @Override
  public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
    PsiFile file = element.getContainingFile();
    if (file == null) {
      return true;
    }
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return true;
    }
    if (virtualFile.getPath().contains(CCUtils.GENERATED_FILES_FOLDER)) {
      return false;
    }
    return true;
  }
}
