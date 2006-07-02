/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.impl.ProjectManagerImpl;

/**
 * @author mike
 */
public class ReloadProjectAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project  = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    ((ProjectManagerImpl)ProjectManager.getInstance()).reloadProject(project, true);
  }
}
