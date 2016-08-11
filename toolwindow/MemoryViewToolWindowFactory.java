package org.jetbrains.debugger.memory.toolwindow;

import com.intellij.execution.Executor;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunContentWithExecutorListener;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerManagerListener;
import org.jetbrains.debugger.memory.view.ClassesFilteredView;
import org.jetbrains.debugger.memory.component.MemoryViewManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class MemoryViewToolWindowFactory implements ToolWindowFactory, DumbAware {
  public final static String TOOL_WINDOW_ID = "Memory View";

  private final JComponent myEmptyContent;

  private final HashMap<XDebugSession, ClassesFilteredView> myMemoryViews = new HashMap<>();

  {
    myEmptyContent = new JBLabel("Run debugging to see loaded classes", SwingConstants.CENTER);
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(XDebuggerManager.TOPIC, new MyDebuggerStatusChangedListener());
    connection.subscribe(RunContentManager.TOPIC, new RunContentWithExecutorListener() {
      @Override
      public void contentSelected(@Nullable RunContentDescriptor descriptor, @NotNull Executor executor) {
        SwingUtilities.invokeLater(() -> updateCurrentMemoryView(project, toolWindow));
      }

      @Override
      public void contentRemoved(@Nullable RunContentDescriptor descriptor, @NotNull Executor executor) {
        SwingUtilities.invokeLater(() -> updateCurrentMemoryView(project, toolWindow));
      }
    });

    ((ToolWindowImpl) toolWindow).getToolWindowManager()
        .addToolWindowManagerListener(new ToolWindowManagerAdapter() {
          @Override
          public void stateChanged() {
            if (!myMemoryViews.isEmpty()) {
              myMemoryViews.values().forEach(classesFilteredView ->
                  classesFilteredView.setNeedReloadClasses(toolWindow.isVisible()));
            }
          }
        });

    ActionGroup group = new DefaultActionGroup(
        ActionManager.getInstance().getAction("MemoryView.ShowOnlyWithInstances"),
        ActionManager.getInstance().getAction("MemoryView.ShowOnlyWithDiff")
    );
    ((ToolWindowEx) toolWindow).setAdditionalGearActions(group);
    toolWindow.getComponent().setLayout(new BorderLayout());

    for (XDebugSession session : XDebuggerManager.getInstance(project).getDebugSessions()) {
      addNewSession(session);
    }

    updateCurrentMemoryView(project, toolWindow);
  }

  private void addNewSession(@NotNull XDebugSession session) {
    ToolWindow toolWindow = getToolWindow(session.getProject());
    if (!myMemoryViews.containsKey(session)) {
      ClassesFilteredView newView = new ClassesFilteredView(session);
      newView.setNeedReloadClasses(toolWindow != null && toolWindow.isVisible());
      myMemoryViews.put(session, newView);
    }
  }

  private void removeSession(@NotNull XDebugSession session) {
    myMemoryViews.remove(session);
  }

  private void updateCurrentMemoryView(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    if (!project.isDisposed()) {
      XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
      if (session != null && myMemoryViews.containsKey(session)) {
        ClassesFilteredView view = myMemoryViews.get(session);
        replaceToolWindowContent(toolWindow, view);
        return;
      }
    }

    replaceToolWindowContent(toolWindow, myEmptyContent);
  }

  private void replaceToolWindowContent(@NotNull ToolWindow toolWindow, JComponent comp) {
    JComponent toolWindowComp = toolWindow.getComponent();
    toolWindowComp.removeAll();
    toolWindowComp.add(comp);
    toolWindowComp.repaint();
  }

  @Nullable
  private ToolWindow getToolWindow(@NotNull Project project) {
    return MemoryViewManager.getInstance().getToolWindow(project);
  }

  private final class MyDebuggerStatusChangedListener implements XDebuggerManagerListener {
    @Override
    public void processStarted(@NotNull XDebugProcess xDebugProcess) {
      SwingUtilities.invokeLater(() -> {
        XDebugSession session = xDebugProcess.getSession();
        addNewSession(session);
        updateView(session);
      });
    }

    @Override
    public void processStopped(@NotNull XDebugProcess xDebugProcess) {
      SwingUtilities.invokeLater(() -> {
        XDebugSession session = xDebugProcess.getSession();
        removeSession(session);
        updateView(session);
      });
    }

    private void updateView(@NotNull XDebugSession debugSession) {
      ToolWindow toolWindow = getToolWindow(debugSession.getProject());
      if (toolWindow != null) {
        updateCurrentMemoryView(debugSession.getProject(), toolWindow);
      }
    }
  }
}
