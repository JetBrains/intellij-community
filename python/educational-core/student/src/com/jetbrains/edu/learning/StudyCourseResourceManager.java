package com.jetbrains.edu.learning;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.courseFormat.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StudyCourseResourceManager {

  /**
   * Gets text of resource file such as test input file or task text in needed format
   *
   * @param fileName name of resource file which should exist in task directory
   * @param wrapHTML if it's necessary to wrap text with html tags
   * @return text of resource file wrapped with html tags if necessary
   */
  @Nullable
  public String getResourceText(@NotNull final Project project, @NotNull final Task task, @NotNull final String fileName, boolean wrapHTML) {
    VirtualFile taskDir = task.getTaskDir(project);
    if (taskDir != null) {
      return StudyUtils.getFileText(taskDir.getCanonicalPath(), fileName, wrapHTML, "UTF-8");
    }
    return null;
  }
}
