package org.jetbrains.idea.svn.config;

import com.intellij.openapi.project.Project;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ConfigureProxiesListener implements ActionListener {
  private final Project myProject;

  public ConfigureProxiesListener(final Project project) {
    myProject = project;
  }

  public void actionPerformed(final ActionEvent e) {
    final SvnConfigureProxiesDialog dialog = new SvnConfigureProxiesDialog(myProject);
    dialog.show();
  }
}
