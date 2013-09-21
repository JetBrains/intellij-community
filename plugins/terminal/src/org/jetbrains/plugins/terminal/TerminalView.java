package org.jetbrains.plugins.terminal;

import com.intellij.icons.AllIcons;
import com.intellij.notification.EventLog;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.UIUtil;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.TabbedTerminalWidget;
import com.jediterm.terminal.ui.TerminalWidget;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

/**
 * @author traff
 */
public class TerminalView {

  private JBTabbedTerminalWidget myTerminalWidget;
  private Project myProject;

  public void initTerminal(final Project project, final ToolWindow toolWindow) {
    myProject = project;
    LocalTerminalDirectRunner terminalRunner = OpenLocalTerminalAction.createTerminalRunner(project);

    toolWindow.setToHideOnEmptyContent(true);

    if (terminalRunner != null) {
      myTerminalWidget = terminalRunner.createTerminalWidget();
      myTerminalWidget.addTabListener(new TabbedTerminalWidget.TabListener() {
        @Override
        public void tabClosed(JediTermWidget terminal) {
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            @Override
            public void run() {
              hideIfNoActiveSessions(toolWindow, myTerminalWidget);
            }
          });
        }
      });
    }

    Content content = createToolWindowContentPanel(terminalRunner, myTerminalWidget, toolWindow);

    toolWindow.getContentManager().addContent(content);

    ((ToolWindowManagerEx)ToolWindowManager.getInstance(myProject)).addToolWindowManagerListener(new ToolWindowManagerListener() {
      @Override
      public void toolWindowRegistered(@NotNull String id) {
      }

      @Override
      public void stateChanged() {
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
        if (window != null) {
          boolean visible = window.isVisible();
          if (visible && toolWindow.getContentManager().getContentCount() == 0) {
            initTerminal(project, window);
          }
        }
      }
    });

    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        if (myTerminalWidget != null) {
          myTerminalWidget.dispose();
        }
      }
    });
  }

  private Content createToolWindowContentPanel(@Nullable LocalTerminalDirectRunner terminalRunner,
                                               JBTabbedTerminalWidget terminalWidget,
                                               ToolWindow toolWindow) {
    SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true) {
      @Override
      public Object getData(@NonNls String dataId) {
        return PlatformDataKeys.HELP_ID.is(dataId) ? EventLog.HELP_ID : super.getData(dataId);
      }
    };

    if (terminalWidget != null) {
      panel.setContent(terminalWidget.getComponent());

      panel.addFocusListener(createFocusListener());
    }

    ActionToolbar toolbar = createToolbar(terminalRunner, terminalWidget, toolWindow);
    toolbar.getComponent().addFocusListener(createFocusListener());
    toolbar.setTargetComponent(panel);
    panel.setToolbar(toolbar.getComponent());

    final Content content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false);
    content.setCloseable(true);

    content.setPreferredFocusableComponent(terminalWidget.getComponent());

    return content;
  }

  private FocusListener createFocusListener() {
    return new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        JComponent component = getComponentToFocus();
        if (component != null) {
          component.requestFocusInWindow();
        }
      }

      @Override
      public void focusLost(FocusEvent e) {

      }
    };
  }

  private JComponent getComponentToFocus() {
    return myTerminalWidget != null ? myTerminalWidget.getComponent() : null;
  }

  public void openLocalSession(Project project, ToolWindow terminal) {
    LocalTerminalDirectRunner terminalRunner = OpenLocalTerminalAction.createTerminalRunner(project);
    openSession(terminal, terminalRunner);
  }

  private void openSession(ToolWindow toolWindow, AbstractTerminalRunner terminalRunner) {
    if (myTerminalWidget == null) {
      myTerminalWidget = terminalRunner.createTerminalWidget();
      toolWindow.getContentManager().removeAllContents(true);
      final Content content = createToolWindowContentPanel(null, myTerminalWidget, toolWindow);
      toolWindow.getContentManager().addContent(content);
    }
    else {
      terminalRunner.openSession(myTerminalWidget);
    }

    toolWindow.activate(new Runnable() {
      @Override
      public void run() {

      }
    }, true);
  }

  public static TerminalView getInstance() {
    return ServiceManager.getService(TerminalView.class);
  }

  private ActionToolbar createToolbar(@Nullable final LocalTerminalDirectRunner terminalRunner,
                                      final JBTabbedTerminalWidget terminal, ToolWindow toolWindow) {
    DefaultActionGroup group = new DefaultActionGroup();

    if (terminalRunner != null) {
      group.add(new NewSession(terminalRunner, terminal));
      group.add(new CloseSession(terminal, toolWindow));
    }

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, false);
  }

  public void createNewSession(Project project, final AbstractTerminalRunner terminalRunner) {
    final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Terminal");

    toolWindow.activate(new Runnable() {
      @Override
      public void run() {
        openSession(toolWindow, terminalRunner);
      }
    }, true);
  }

  private static void hideIfNoActiveSessions(final ToolWindow toolWindow, JBTabbedTerminalWidget terminal) {
    if (terminal.isNoActiveSessions()) {
      toolWindow.getContentManager().removeAllContents(true);
    }
  }

  private static class NewSession extends DumbAwareAction {
    private final LocalTerminalDirectRunner myTerminalRunner;
    private final TerminalWidget myTerminal;

    public NewSession(LocalTerminalDirectRunner terminalRunner, TerminalWidget terminal) {
      super("New Session", "Create New Terminal Session", AllIcons.General.Add);
      myTerminalRunner = terminalRunner;
      myTerminal = terminal;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myTerminalRunner.openSession(myTerminal);
    }
  }

  private class CloseSession extends DumbAwareAction {
    private final JBTabbedTerminalWidget myTerminal;
    private ToolWindow myToolWindow;

    public CloseSession(JBTabbedTerminalWidget terminal, ToolWindow toolWindow) {
      super("Close Session", "Close Terminal Session", AllIcons.Actions.Delete);
      myTerminal = terminal;
      myToolWindow = toolWindow;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myTerminal.closeCurrentSession();

      hideIfNoActiveSessions(myToolWindow, myTerminal);
    }
  }
}
