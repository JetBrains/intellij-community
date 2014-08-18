package com.jetbrains.python.edu;

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiErrorElement;
import org.jetbrains.annotations.NotNull;
import com.jetbrains.python.edu.course.TaskFile;

/**
 * author: liana
 * data: 7/23/14.
 */
public class StudyHighlightErrorFilter extends HighlightErrorFilter {
  @Override
  public boolean shouldHighlightErrorElement(@NotNull final PsiErrorElement element) {
    VirtualFile file = element.getContainingFile().getVirtualFile();
    Project project = element.getProject();
    StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    TaskFile taskFile = taskManager.getTaskFile(file);
    return taskFile == null;
  }
}
