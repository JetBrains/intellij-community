package org.jetbrains.plugins.terminal;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;

/**
 * @author traff
 */
public class TerminalToolWindowFactory implements ToolWindowFactory {
  @Override
  public void createToolWindowContent(Project project, ToolWindow toolWindow) {
    TerminalView terminalView = TerminalView.getInstance();
    terminalView.createTerminal(project, toolWindow);
  }
}
