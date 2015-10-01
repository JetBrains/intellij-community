package com.jetbrains.edu.learning.highlighting;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyImportStatement;
import org.jetbrains.annotations.NotNull;

public class PyStudyInspectionExtension extends PyInspectionExtension {

  @Override
  public boolean ignoreUnresolvedReference(@NotNull PyElement element, @NotNull PsiReference reference) {
    final PsiFile file = element.getContainingFile();
    if (StudyTaskManager.getInstance(file.getProject()).getCourse() != null) {
      if (PsiTreeUtil.getParentOfType(element, PyImportStatement.class) != null) {
        return false;
      }
      return true;
    }
    return false;
  }

}
