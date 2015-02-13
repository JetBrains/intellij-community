package com.jetbrains.edu.learning.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.courseFormat.Task;
import org.jetbrains.annotations.NotNull;

public abstract class StudyTestRunner {
  private static final String ourStudyPrefix = "#educational_plugin";
  public static final String TEST_OK = "test OK";
  private static final String TEST_FAILED = "FAILED + ";
  protected final Task myTask;
  protected final VirtualFile myTaskDir;

  public StudyTestRunner(@NotNull final Task task, @NotNull final VirtualFile taskDir) {
    myTask = task;
    myTaskDir = taskDir;
  }

  public abstract Process createCheckProcess(@NotNull final Project project, @NotNull final String executablePath) throws ExecutionException;

  @NotNull
  public String getTestsOutput(@NotNull final ProcessOutput processOutput) {
    for (String line : processOutput.getStdoutLines()) {
      if (line.contains(ourStudyPrefix)) {
        if (line.contains(TEST_OK)) {
          continue;
        }
        int messageStart = line.indexOf(TEST_FAILED);
        return line.substring(messageStart + TEST_FAILED.length());
      }
    }

    return TEST_OK;
  }
}
