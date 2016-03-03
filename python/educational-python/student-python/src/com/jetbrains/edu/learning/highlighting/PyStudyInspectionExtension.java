package com.jetbrains.edu.learning.highlighting;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyImportStatementBase;
import org.jetbrains.annotations.NotNull;

public class PyStudyInspectionExtension extends PyInspectionExtension {

  @Override
  public boolean ignoreUnresolvedReference(@NotNull PyElement element, @NotNull PsiReference reference) {
    final PsiFile file = element.getContainingFile();
    final Project project = file.getProject();

    if (StudyTaskManager.getInstance(project).getCourse() == null) {
      return false;
    }
    TaskFile taskFile = StudyUtils.getTaskFile(project, file.getVirtualFile());
    if (taskFile == null || taskFile.isUserCreated() || taskFile.isHighlightErrors()) {
      return false;
    }
    if (PsiTreeUtil.getParentOfType(element, PyImportStatementBase.class) != null) {
      return false;
    }
    return true;
  }

}
