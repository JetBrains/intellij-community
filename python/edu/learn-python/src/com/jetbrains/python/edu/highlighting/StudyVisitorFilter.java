package com.jetbrains.python.edu.highlighting;

import com.intellij.psi.PsiFile;
import com.jetbrains.python.edu.StudyTaskManager;
import com.jetbrains.python.inspections.PythonVisitorFilter;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import org.jetbrains.annotations.NotNull;

public class StudyVisitorFilter implements PythonVisitorFilter {
  @Override
  public boolean isSupported(@NotNull final Class visitorClass, @NotNull final PsiFile file) {
    if (StudyTaskManager.getInstance(file.getProject()).getCourse() == null) return true;
    if (visitorClass == PyUnresolvedReferencesInspection.class) {
      return false;
    }
    return true;
  }
}
