package org.jetbrains.idea.maven.runner;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;

public class ConsoleAdapterImpl extends ConsoleAdapter {
  private final ConsoleView myConsoleView;

  public ConsoleAdapterImpl(ConsoleView view, int outputLevel, boolean printStrackTrace) {
    super(outputLevel, printStrackTrace);
    myConsoleView = view;
  }

  public boolean canPause() {
    return myConsoleView.canPause();
  }

  public boolean isOutputPaused() {
    return myConsoleView.isOutputPaused();
  }

  public void setOutputPaused(boolean outputPaused) {
    myConsoleView.setOutputPaused(outputPaused);
  }

  public void attachToProcess( ProcessHandler processHandler) {
    myConsoleView.attachToProcess(processHandler);
  }

  protected void doPrint(String text, OutputType type) {
    ConsoleViewContentType contentType;
    switch (type) {
      case SYSTEM:
        contentType = ConsoleViewContentType.SYSTEM_OUTPUT;
        break;
      case ERROR:
        contentType = ConsoleViewContentType.ERROR_OUTPUT;
        break;
      case NORMAL:
      default:
        contentType = ConsoleViewContentType.NORMAL_OUTPUT;
    }
   myConsoleView.print(text, contentType);
  }
}