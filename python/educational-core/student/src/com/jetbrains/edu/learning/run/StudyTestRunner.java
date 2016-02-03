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
  private static final String CONGRATS_MESSAGE = "CONGRATS_MESSAGE ";
  protected final Task myTask;
  protected final VirtualFile myTaskDir;

  public StudyTestRunner(@NotNull final Task task, @NotNull final VirtualFile taskDir) {
    myTask = task;
    myTaskDir = taskDir;
  }

  public abstract Process createCheckProcess(@NotNull final Project project, @NotNull final String executablePath) throws ExecutionException;

  @NotNull
  public TestsOutput getTestsOutput(@NotNull final ProcessOutput processOutput) {
    String congratulations = "Congratulations!";
    for (String line : processOutput.getStdoutLines()) {
      if (line.startsWith(ourStudyPrefix)) {
        if (line.contains(TEST_OK)) {
          continue;
        }

        if (line.contains(CONGRATS_MESSAGE)) {
          congratulations = line.substring(line.indexOf(CONGRATS_MESSAGE) + CONGRATS_MESSAGE.length());
        }

        if (line.contains(TEST_FAILED)) {
          return new TestsOutput(false, line.substring(line.indexOf(TEST_FAILED) + TEST_FAILED.length()));
        }
      }
    }

    return new TestsOutput(true, congratulations);
  }

  public static class TestsOutput {
    private final boolean isSuccess;
    private final String myMessage;

    public TestsOutput(boolean isSuccess, @NotNull final String message) {
      this.isSuccess = isSuccess;
      myMessage = message;
    }

    public boolean isSuccess() {
      return isSuccess;
    }

    public String getMessage() {
      return myMessage;
    }
  }

}
