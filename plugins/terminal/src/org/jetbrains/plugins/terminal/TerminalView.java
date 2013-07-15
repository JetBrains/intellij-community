package org.jetbrains.plugins.terminal;

import com.intellij.icons.AllIcons;
import com.intellij.notification.EventLog;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.jediterm.terminal.ui.TerminalWidget;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

/**
 * @author traff
 */
public class TerminalView {

  private TerminalWidget myTerminalWidget;

  public void createTerminal(Project project, ToolWindow toolWindow) {

    LocalTerminalDirectRunner terminalRunner = OpenLocalTerminalAction.createTerminalRunner(project);

    if (terminalRunner != null) {
      myTerminalWidget = terminalRunner.createTerminalWidget();
    }

    final Content content = createToolWindowContentPanel(terminalRunner, myTerminalWidget);
    toolWindow.getContentManager().addContent(content);
  }

  private Content createToolWindowContentPanel(@Nullable LocalTerminalDirectRunner terminalRunner, TerminalWidget terminalWidget) {
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

    ActionToolbar toolbar = createToolbar(terminalRunner, terminalWidget);
    toolbar.getComponent().addFocusListener(createFocusListener());
    toolbar.setTargetComponent(panel);
    panel.setToolbar(toolbar.getComponent());

    final Content content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false);

    if (getComponentToFocus() != null) {
      content.setPreferredFocusableComponent(getComponentToFocus());
    }
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

  private void openSession(ToolWindow terminal, AbstractTerminalRunner terminalRunner) {
    if (myTerminalWidget == null) {
      myTerminalWidget = terminalRunner.createTerminalWidget();
      terminal.getContentManager().removeAllContents(true);
      final Content content = createToolWindowContentPanel(null, myTerminalWidget);
      terminal.getContentManager().addContent(content);
    }
    else {
      terminalRunner.openSession(myTerminalWidget);
    }

    terminal.activate(new Runnable() {
      @Override
      public void run() {

      }
    }, true);
  }

  public static TerminalView getInstance() {
    return ServiceManager.getService(TerminalView.class);
  }

  private static ActionToolbar createToolbar(@Nullable final LocalTerminalDirectRunner terminalRunner,
                                             final TerminalWidget terminal) {
    DefaultActionGroup group = new DefaultActionGroup();

    if (terminalRunner != null) {
      group.add(new NewSession(terminalRunner, terminal));
    }

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, false);
  }

  public void createNewSession(Project project, AbstractTerminalRunner terminalRunner) {
    ToolWindow terminal = ToolWindowManager.getInstance(project).getToolWindow("Terminal");

    openSession(terminal, terminalRunner);

    terminal.activate(new Runnable() {
      @Override
      public void run() {

      }
    }, true);
  }

  private static class NewSession extends AnAction {
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
}
