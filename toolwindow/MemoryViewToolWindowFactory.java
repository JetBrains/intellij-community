package org.jetbrains.debugger.memory.toolwindow;

import com.intellij.execution.Executor;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunContentWithExecutorListener;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerManagerListener;
import icons.MemoryViewIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.memory.component.MemoryViewManager;
import org.jetbrains.debugger.memory.view.ClassesFilteredView;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryViewToolWindowFactory implements ToolWindowFactory, DumbAware {
  private static final Logger LOG = Logger.getInstance(ClassesFilteredView.class);
  public final static String TOOL_WINDOW_ID = "Memory View";

  private final JComponent myEmptyContent;
  private final JComponent myMemoryViewNotSupportedContent;

  private final Map<XDebugSession, ClassesFilteredView> myMemoryViews = new ConcurrentHashMap<>();

  @Nullable
  private ClassesFilteredView myCurrentView = null;

  {
    myEmptyContent = new JBLabel("Run debugging to see loaded classes", SwingConstants.CENTER);
    myMemoryViewNotSupportedContent = new JBLabel("The Memory View not available for this session",
        SwingConstants.CENTER);
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
            if (!myMemoryViews.isEmpty() && !toolWindow.isDisposed()) {
              myMemoryViews.values().forEach(classesFilteredView ->
                  classesFilteredView.setActive(toolWindow.isVisible()));
            }
          }
        }, project);

    ActionGroup group = new DefaultActionGroup(
        ActionManager.getInstance().getAction("MemoryView.ShowOnlyWithInstances"),
        ActionManager.getInstance().getAction("MemoryView.ShowOnlyWithDiff"),
        ActionManager.getInstance().getAction("MemoryView.ShowOnlyTracked"),
        Separator.getInstance(),
        ActionManager.getInstance().getAction("MemoryView.EnableTrackingWithClosedWindow")
    );
    ((ToolWindowEx) toolWindow).setAdditionalGearActions(group);
    toolWindow.getComponent().setLayout(new BorderLayout());

    for (XDebugSession session : XDebuggerManager.getInstance(project).getDebugSessions()) {
      addNewSession(session);
    }

    updateCurrentMemoryView(project, toolWindow);
  }

  private void addNewSession(@NotNull XDebugSession session) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    ToolWindow toolWindow = getToolWindow(session.getProject());
    if (!myMemoryViews.containsKey(session)) {
      try {
        ClassesFilteredView newView = new ClassesFilteredView(session);
        newView.setActive(toolWindow != null && toolWindow.isVisible());
        myMemoryViews.put(session, newView);
      } catch (Throwable e) {
        LOG.warn("Cannot create new instance of the memory view", e);
      }
    }
  }

  private void removeSession(@NotNull XDebugSession session) {
    ClassesFilteredView removed = myMemoryViews.remove(session);
    if (removed != null) {
      Disposer.dispose(removed);
    }
  }

  private void updateCurrentMemoryView(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    if (!project.isDisposed()) {
      XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
      if (session != null) {
        ClassesFilteredView view;
        if (myMemoryViews.containsKey(session)) {
          view = myMemoryViews.get(session);
          replaceToolWindowContent(toolWindow, view);
        } else {
          view = null;
          replaceToolWindowContent(toolWindow, myMemoryViewNotSupportedContent);
        }

        if (myCurrentView != null) {
          myCurrentView.setActive(false);
        }

        myCurrentView = view;
        return;
      }
    }

    replaceToolWindowContent(toolWindow, myEmptyContent);
  }

  private void replaceToolWindowContent(@NotNull ToolWindow toolWindow, JComponent comp) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    JComponent toolWindowComp = toolWindow.getComponent();
    toolWindowComp.removeAll();
    toolWindowComp.add(comp);
    toolWindowComp.repaint();
  }

  @Nullable
  private ToolWindow getToolWindow(@NotNull Project project) {
    return MemoryViewManager.getInstance().getToolWindow(project);
  }

  public static class Condition implements com.intellij.openapi.util.Condition<Project> {
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
      XDebugSession session = xDebugProcess.getSession();
      removeSession(session);
      SwingUtilities.invokeLater(() -> updateView(session));
    }

    private void updateView(@NotNull XDebugSession debugSession) {
      Project project = debugSession.getProject();
      if (!project.isDisposed()) {
        ToolWindow toolWindow = getToolWindow(project);
        if (toolWindow != null) {
          updateCurrentMemoryView(project, toolWindow);
        }
      }
    }
  }
}
