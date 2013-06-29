package org.jetbrains.plugins.terminal;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import icons.TerminalIcons;

/**
 * @author traff
 */
public class OpenLocalTerminalAction extends AnAction implements DumbAware {
  public OpenLocalTerminalAction() {
    super("Open Terminal...", null, TerminalIcons.OpenTerminal);
  }

  @Override
  public void update(final AnActionEvent e) {
    boolean enabled = SystemInfo.isUnix;
    e.getPresentation().setVisible(enabled);
    e.getPresentation().setEnabled(enabled);
  }

  public void actionPerformed(final AnActionEvent e) {
    runLocalTerminal(e);
  }

  public void runLocalTerminal(AnActionEvent event) {
    final Project project = event.getData(PlatformDataKeys.PROJECT);
    runLocalTerminal(project);
  }

  public static void runLocalTerminal(final Project project) {
    String[] terminalCommand = SystemInfo.isMac ? new String[]{"/bin/bash", "--login"} : new String[]{"/bin/bash"};

    new LocalTerminalDirectRunner(project, terminalCommand).run();
  }
}
