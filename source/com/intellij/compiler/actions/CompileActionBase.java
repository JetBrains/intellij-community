package com.intellij.compiler.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;

public abstract class CompileActionBase extends AnAction {

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      return;
    }

    doAction(dataContext, project);
  }

  protected abstract void doAction(final DataContext dataContext, final Project project);
}
