package org.jetbrains.idea.maven.builder.executor;

import com.intellij.execution.filters.*;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.builder.logger.MavenLogUtil;
import org.jetbrains.idea.maven.core.util.ErrorHandler;

/**
 * @author Vladislav.Kaznacheev
 */
public class ConsoleAdapter {
  private static final String COMPILE_REGEXP_SOURCE =
    RegexpFilter.FILE_PATH_MACROS + ":\\[" + RegexpFilter.LINE_MACROS + "," + RegexpFilter.COLUMN_MACROS + "]";

  protected ConsoleView consoleView;

  private final int outputLevel;

  public ConsoleAdapter(final int outputLevel) {
    this.outputLevel = outputLevel;
  }

  public ConsoleView createConsole(Project project) {
    TextConsoleBuilderFactory factory = TextConsoleBuilderFactory.getInstance();

    TextConsoleBuilder builder = factory.createBuilder(project);

    for (Filter filter : getFilters(project)) {
      builder.addFilter(filter);
    }

    consoleView = builder.getConsole();
    return consoleView; 
  }


  private static Filter[] getFilters(final Project project) {
    return new Filter[]{new ExceptionFilter(project), new RegexpFilter(project, COMPILE_REGEXP_SOURCE)};
  }

  protected boolean isNotSuppressed(final int level) {
    return level >= outputLevel;
  }

  protected void systemMessage(int level, String string, Throwable throwable) {
    if (level == MavenLogUtil.LEVEL_FATAL) {
      consoleView.setOutputPaused(false);
    }
    if (isNotSuppressed(level)) {
      StringBuilder builder = new StringBuilder(string);
      if(throwable!=null){
        final String message = throwable.getMessage();
        if(message!=null) {
          builder.append(": ");
          builder.append(message);
        }
      }
      builder.append(MavenLogUtil.LINE_SEPARATOR);

      printMessage(level, builder.toString(), throwable, ConsoleViewContentType.SYSTEM_OUTPUT);
    }
  }

  protected void printMessage(final int level, final String string, final Throwable throwable, final ConsoleViewContentType output) {
    consoleView.print(MavenLogUtil.composeLine(level, string), output);
    if (throwable != null) {
      consoleView.print(ErrorHandler.getFullStackTrace(ErrorHandler.getRootCause(throwable)), ConsoleViewContentType.ERROR_OUTPUT);
    }
  }

  public boolean isOutputPaused() {
    return consoleView.isOutputPaused();
  }

  public void setOutputPaused(boolean outputPaused) {
    consoleView.setOutputPaused(outputPaused);
  }
}
