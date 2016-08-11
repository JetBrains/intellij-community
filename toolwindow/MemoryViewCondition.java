package org.jetbrains.debugger.memory.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerManagerListener;
import icons.MemoryViewIcons;
import org.jetbrains.debugger.memory.component.MemoryViewManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;

public class MemoryViewCondition implements Condition<Project> {
  @Override
  public boolean value(Project project) {
    MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(XDebuggerManager.TOPIC, new XDebuggerManagerListener() {
      @Override
      public void processStarted(@NotNull XDebugProcess debugProcess) {
        updateIcon(debugProcess.getSession().getProject(), true);
      }

      @Override
      public void processStopped(@NotNull XDebugProcess debugProcess) {
        Project project = debugProcess.getSession().getProject();
        boolean enabled = Arrays.stream(XDebuggerManager.getInstance(project)
            .getDebugSessions()).anyMatch(session -> !session.getDebugProcess().equals(debugProcess));
        updateIcon(project, enabled);
      }

      private void updateIcon(@NotNull Project project, boolean enabled) {
        ToolWindow toolWindow = MemoryViewManager.getInstance().getToolWindow(project);
        if (toolWindow != null) {
          SwingUtilities.invokeLater(() ->
              toolWindow.setIcon(enabled ? MemoryViewIcons.MAIN_ENABLED : MemoryViewIcons.MAIN_DISABLED));
        }
      }
    });
    return true;
  }
}
