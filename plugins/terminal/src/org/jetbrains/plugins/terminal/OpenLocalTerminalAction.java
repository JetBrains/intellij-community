package org.jetbrains.plugins.terminal;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import icons.TerminalIcons;

import java.nio.charset.Charset;

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
    final Project project = event.getData(PlatformDataKeys.PROJECT);
    setupRemoteCredentialsAndRunTerminal(project);
  }

  public static void setupRemoteCredentialsAndRunTerminal(final Project project) {
    new LocalTerminalDirectRunner(project, Charset.defaultCharset(), "/bin/bash").run();
  }
}
