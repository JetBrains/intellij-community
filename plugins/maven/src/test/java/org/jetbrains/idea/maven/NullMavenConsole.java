package org.jetbrains.idea.maven;

import org.jetbrains.idea.maven.embedder.MavenConsole;
import com.intellij.execution.process.ProcessHandler;

public class NullMavenConsole extends MavenConsole {
  public NullMavenConsole() {
    super(0, false);
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
  }

  protected void doPrint(String text, OutputType type) {
  }
}
