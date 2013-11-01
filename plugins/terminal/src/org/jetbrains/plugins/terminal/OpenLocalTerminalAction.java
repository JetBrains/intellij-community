package org.jetbrains.plugins.terminal;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import icons.TerminalIcons;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class OpenLocalTerminalAction extends AnAction implements DumbAware {
  public OpenLocalTerminalAction() {
    super("Open Terminal...", null, TerminalIcons.OpenTerminal);
  }

  @Override
  public void update(final AnActionEvent e) {
    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(true);
  }

  public void actionPerformed(final AnActionEvent e) {
    runLocalTerminal(e);
  }

  public void runLocalTerminal(AnActionEvent event) {
    final Project project = event.getData(CommonDataKeys.PROJECT);
    runLocalTerminal(project);
  }

  public static void runLocalTerminal(final Project project) {
    ToolWindow terminal = ToolWindowManager.getInstance(project).getToolWindow("Terminal");
    if (terminal.isActive()) {
      TerminalView.getInstance(project).openLocalSession(project, terminal);
    }
    terminal.activate(new Runnable() {
      @Override
      public void run() {

      }
    }, true);
  }

  @NotNull
  public static LocalTerminalDirectRunner createTerminalRunner(Project project) {
    return new LocalTerminalDirectRunner(project);
  }
}
