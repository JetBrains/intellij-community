package com.intellij.ide.actions;

import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;

/**
 *
 */
public class NewProjectAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    NewProjectUtil.createNewProject(PlatformDataKeys.PROJECT.getData(e.getDataContext()), null);
  }
}
