package org.jetbrains.idea.maven.runner;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.Key;

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

  public void attachToProcess(ProcessHandler processHandler) {
    myConsoleView.attachToProcess(processHandler);
    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        beforePrint();
      }
    });
  }

  protected void doPrint(String text, OutputType type) {
    beforePrint();

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

  protected void beforePrint() {
  }
}