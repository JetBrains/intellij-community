package com.jetbrains.edu.learning;

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.edu.learning.course.TaskFile;
import org.jetbrains.annotations.NotNull;

public class StudyHighlightErrorFilter extends HighlightErrorFilter{
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
    TaskFile taskFile = StudyTaskManager.getInstance(element.getProject()).getTaskFile(virtualFile);
    return taskFile == null || taskFile.isHighlightErrors();
  }
}
