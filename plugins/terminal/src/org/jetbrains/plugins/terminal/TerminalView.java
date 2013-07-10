package org.jetbrains.plugins.terminal;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.notification.EventLog;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.jediterm.terminal.ui.TerminalWidget;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * @author traff
 */
public class TerminalView {

  public void openTerminal(Project project, ToolWindow toolWindow) {

    LocalTerminalDirectRunner terminalRunner = OpenLocalTerminalAction.createTerminalRunner(project);
    
    TerminalWidget terminalWidget = terminalRunner.createTerminalWidget();

    SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true) {
      @Override
      public Object getData(@NonNls String dataId) {
        return PlatformDataKeys.HELP_ID.is(dataId) ? EventLog.HELP_ID : super.getData(dataId);
      }
    };
    panel.setContent(terminalWidget.getComponent());

    ActionToolbar toolbar = createToolbar(project, terminalRunner, terminalWidget);
    toolbar.setTargetComponent(panel);
    panel.setToolbar(toolbar.getComponent());

    final Content content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false);

    toolWindow.getContentManager().addContent(content);
  }

  public static TerminalView getInstance() {
    return ServiceManager.getService(TerminalView.class);
  }

  private static ActionToolbar createToolbar(Project project, final LocalTerminalDirectRunner terminalRunner, final TerminalWidget terminal) {
    DefaultActionGroup group = new DefaultActionGroup();
    
    group.add(new NewSession(terminalRunner, terminal));

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, false);
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
