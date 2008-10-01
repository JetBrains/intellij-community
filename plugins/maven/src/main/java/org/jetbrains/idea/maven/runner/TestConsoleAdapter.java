package org.jetbrains.idea.maven.runner;

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.util.Key;
import org.apache.maven.execution.MavenExecutionRequest;

public class TestConsoleAdapter extends ConsoleAdapter {
  public TestConsoleAdapter() {
    super(MavenExecutionRequest.LOGGING_LEVEL_DEBUG, true);
  }

  public boolean canPause() {
    return false;
  }

  public boolean isOutputPaused() {
    return false;
  }

  public void setOutputPaused(boolean outputPaused) {
  }

  public void attachToProcess(ProcessHandler processHandler) {
    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        System.out.print(event.getText());
      }

      @Override
      public void processTerminated(ProcessEvent event) {
        System.out.println("PROCESS TERMINATED: " + event.getExitCode());
      }
    });
  }

  protected void doPrint(String text, OutputType type) {
    System.out.print(text);
  }
}
