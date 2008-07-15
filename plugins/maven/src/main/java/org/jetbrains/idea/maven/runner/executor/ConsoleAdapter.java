package org.jetbrains.idea.maven.runner.executor;

import com.intellij.execution.filters.*;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import org.apache.maven.plugin.AbstractMojoExecutionException;
import org.jetbrains.idea.maven.runner.logger.MavenLogUtil;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author Vladislav.Kaznacheev
 */
public class ConsoleAdapter {
  private static final String COMPILE_REGEXP_SOURCE =
      RegexpFilter.FILE_PATH_MACROS + ":\\[" + RegexpFilter.LINE_MACROS + "," + RegexpFilter.COLUMN_MACROS + "]";

  private ConsoleView myConsoleView;

  private final int myOutputLevel;
  private boolean myPrintStrackTrace;

  public ConsoleAdapter(int outputLevel, boolean printStrackTrace) {
    myOutputLevel = outputLevel;
    myPrintStrackTrace = printStrackTrace;
  }

  public ConsoleView createConsole(Project project) {
    TextConsoleBuilderFactory factory = TextConsoleBuilderFactory.getInstance();

    TextConsoleBuilder builder = factory.createBuilder(project);

    for (Filter filter : getFilters(project)) {
      builder.addFilter(filter);
    }

    myConsoleView = builder.getConsole();
    return myConsoleView;
  }


  private static Filter[] getFilters(final Project project) {
    return new Filter[]{new ExceptionFilter(project), new RegexpFilter(project, COMPILE_REGEXP_SOURCE)};
  }

  protected boolean isNotSuppressed(final int level) {
    return level >= myOutputLevel;
  }

  protected void systemMessage(int level, String string, Throwable throwable) {
    printMessage(level, string, throwable, ConsoleViewContentType.SYSTEM_OUTPUT);
  }

  protected void printMessage(int level, String string, Throwable throwable, ConsoleViewContentType output) {
    if (myConsoleView == null) {
      return;
    }

    if (level == MavenLogUtil.LEVEL_AUTO) {
      level = MavenLogUtil.getLevel(string);
      if (isNotSuppressed(level)) {
        myConsoleView.print(string, output);
      }
    }
    else {
      myConsoleView.print(MavenLogUtil.composeLine(level, string), output);
    }

    if (level == MavenLogUtil.LEVEL_FATAL) {
      myConsoleView.setOutputPaused(false);
    }

    if (throwable != null) {
      String message = throwable.getMessage();

      if (throwable instanceof AbstractMojoExecutionException) {
        String longMessage = ((AbstractMojoExecutionException)throwable).getLongMessage();
        if (longMessage != null) {
          if (message == null) {
            message = longMessage;
          }
          else {
            message += MavenLogUtil.LINE_SEPARATOR + MavenLogUtil.LINE_SEPARATOR + longMessage;
          }
        }
      }
      if (message != null) {
        message += MavenLogUtil.LINE_SEPARATOR;
        myConsoleView.print(MavenLogUtil.LINE_SEPARATOR + MavenLogUtil.composeLine(MavenLogUtil.LEVEL_INFO, message), output);
      }

      if (myPrintStrackTrace) {
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        try {
          throwable.printStackTrace(printWriter);
          myConsoleView.print(writer.getBuffer().toString(), ConsoleViewContentType.ERROR_OUTPUT);
        }
        finally {
          printWriter.close();
        }
      }
    }
  }

  public boolean canPause() {
    return myConsoleView != null && myConsoleView.canPause();
  }

  public boolean isOutputPaused() {
    return myConsoleView != null && myConsoleView.isOutputPaused();
  }

  public void setOutputPaused(boolean outputPaused) {
    if (myConsoleView != null) {
      myConsoleView.setOutputPaused(outputPaused);
    }
  }

  protected void attachToProcess(final ProcessHandler processHandler) {
    if (myConsoleView != null) {
      myConsoleView.attachToProcess(processHandler);
    }
  }
}
