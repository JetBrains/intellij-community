package com.jetbrains.edu.learning.checker;

import com.intellij.execution.process.ProcessOutput;
import org.jetbrains.annotations.NotNull;

public class StudyTestsOutputParser {
  private static final String ourStudyPrefix = "#educational_plugin";
  public static final String TEST_OK = "test OK";
  private static final String TEST_FAILED = "FAILED + ";
  private static final String CONGRATS_MESSAGE = "CONGRATS_MESSAGE ";
  public static final String CONGRATULATIONS = "Congratulations!";

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

  @NotNull
  public static TestsOutput getTestsOutput(@NotNull final ProcessOutput processOutput) {
    String congratulations = CONGRATULATIONS;
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
}
