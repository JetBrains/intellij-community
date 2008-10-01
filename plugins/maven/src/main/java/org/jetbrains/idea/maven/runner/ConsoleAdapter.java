package org.jetbrains.idea.maven.runner;

import com.intellij.execution.process.ProcessHandler;
import org.apache.maven.plugin.AbstractMojoExecutionException;
import org.jetbrains.idea.maven.utils.MavenLogUtil;

import java.io.PrintWriter;
import java.io.StringWriter;

public abstract class ConsoleAdapter {
  public enum OutputType {
    NORMAL, SYSTEM, ERROR
  }

  private final int myOutputLevel;
  private final boolean myPrintStrackTrace;

  public ConsoleAdapter(int outputLevel, boolean printStrackTrace) {
    myOutputLevel = outputLevel;
    myPrintStrackTrace = printStrackTrace;
  }

  public boolean isSuppressed(final int level) {
    return level < myOutputLevel;
  }

  public abstract boolean canPause();

  public abstract boolean isOutputPaused();

  public abstract void setOutputPaused(boolean outputPaused);

  public abstract void attachToProcess(ProcessHandler processHandler);

  public void systemMessage(int level, String string, Throwable throwable) {
    printMessage(level, string, throwable, OutputType.SYSTEM);
  }

  public void printMessage(int level, String string, Throwable throwable, OutputType type) {
    if (level == MavenLogUtil.LEVEL_AUTO) {
      level = MavenLogUtil.getLevel(string);
      if (!isSuppressed(level)) {
        doPrint(string, type);
      }
    }
    else {
      doPrint(MavenLogUtil.composeLine(level, string), type);
    }

    if (level == MavenLogUtil.LEVEL_FATAL) {
      setOutputPaused(false);
    }

    if (throwable != null) {
      String message = null;

      Throwable temp = throwable;
      while (temp != null) {
        if (temp instanceof AbstractMojoExecutionException) {
          message = appendExecutionFailureMessage(message, temp.getMessage());
          message = appendExecutionFailureMessage(message, ((AbstractMojoExecutionException)temp).getLongMessage());

          if (temp.getCause() != null) {
            message = appendExecutionFailureMessage(message, temp.getCause().getMessage());
          }
          break;
        }
        temp = temp.getCause();
      }

      if (message == null) message = throwable.getMessage();

      if (message != null) {
        message += MavenLogUtil.LINE_SEPARATOR;
        doPrint(MavenLogUtil.LINE_SEPARATOR + MavenLogUtil.composeLine(MavenLogUtil.LEVEL_ERROR, message), type);
      }

      if (myPrintStrackTrace) {
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        try {
          throwable.printStackTrace(printWriter);
          doPrint(MavenLogUtil.LINE_SEPARATOR + writer.getBuffer().toString(), OutputType.ERROR);
        }
        finally {
          printWriter.close();
        }
      }
      else {
        doPrint(MavenLogUtil.LINE_SEPARATOR +
                "To view full stack traces, please go to the Maven Settings->General and check the 'Print Exception Stack Traces' box.",
                type);
      }
    }
  }

  private String appendExecutionFailureMessage(String message, String newMessage) {
    if (message == null) return newMessage;
    if (newMessage == null) return message;
    return message + MavenLogUtil.LINE_SEPARATOR + MavenLogUtil.LINE_SEPARATOR + newMessage;
  }

  protected abstract void doPrint(String text, OutputType type);
}
