package com.intellij.debugger.actions;

import com.intellij.debugger.settings.ThreadsViewSettings;
import com.intellij.debugger.ui.PropertiesDialog;
import com.intellij.debugger.ui.impl.ThreadsDebuggerTree;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;

/**
 * User: lex
 * Date: Sep 26, 2003
 * Time: 4:40:12 PM
 */
public class CustomizeThreadsViewAction extends DebuggerAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    PropertiesDialog dialog = new PropertiesDialog(ThreadsViewSettings.getInstance().getConfigurable(), project);
    dialog.show();
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(getTree(e.getDataContext()) instanceof ThreadsDebuggerTree);
    e.getPresentation().setText("Customize View...");
  }
}
