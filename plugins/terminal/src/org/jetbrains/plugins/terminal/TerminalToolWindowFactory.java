package org.jetbrains.plugins.terminal;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;

/**
 * @author traff
 */
public class TerminalToolWindowFactory implements ToolWindowFactory, DumbAware {
  public static final String TOOL_WINDOW_ID = "Terminal";
  
  @Override
  public void createToolWindowContent(Project project, ToolWindow toolWindow) {
    TerminalView terminalView = TerminalView.getInstance(project);
    terminalView.initTerminal(toolWindow);
  }
}
