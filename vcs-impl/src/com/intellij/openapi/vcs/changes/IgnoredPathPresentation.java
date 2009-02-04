package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class IgnoredPathPresentation {
  private final Project myProject;

  public IgnoredPathPresentation(Project project) {
    myProject = project;
  }

  public String alwaysRelative(@NotNull final String path) {
    final File file = new File(path);
    String relativePath = path;
    if (file.isAbsolute()) {
      relativePath = ChangesUtil.getProjectRelativePath(myProject, file);
      if (relativePath == null) {
        relativePath = path;
      }
    }
    return FileUtil.toSystemIndependentName(relativePath);
  }
}
