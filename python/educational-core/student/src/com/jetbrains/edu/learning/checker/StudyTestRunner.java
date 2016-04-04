package com.jetbrains.edu.learning.checker;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.courseFormat.Task;
import org.jetbrains.annotations.NotNull;

public abstract class StudyTestRunner {protected final Task myTask;
  protected final VirtualFile myTaskDir;

  public StudyTestRunner(@NotNull final Task task, @NotNull final VirtualFile taskDir) {
    myTask = task;
    myTaskDir = taskDir;
  }

  public abstract Process createCheckProcess(@NotNull final Project project, @NotNull final String executablePath) throws ExecutionException;

}
