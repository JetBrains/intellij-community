/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.tools;

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.WindowManager;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 30, 2005
 */
class ToolProcessAdapter extends ProcessAdapter {
  private final Project myProject;
  private final boolean mySynchronizeAfterExecution;
  private final String myName;

  public ToolProcessAdapter(Project project, final boolean synchronizeAfterExecution, final String name) {
    myProject = project;
    mySynchronizeAfterExecution = synchronizeAfterExecution;
    myName = name;
  }

  public void processTerminated(ProcessEvent event) {
    final String message = ToolsBundle.message("tools.completed.message", myName, event.getExitCode());

    if (mySynchronizeAfterExecution) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          VirtualFileManager.getInstance().refresh(true, new Runnable() {
            public void run() {
              if (ProjectManagerEx.getInstanceEx().isProjectOpened(myProject)) {
                WindowManager.getInstance().getStatusBar(myProject).setInfo(message);
              }
            }
          });
        }
      });
    }
    if (ProjectManagerEx.getInstanceEx().isProjectOpened(myProject)) {
      WindowManager.getInstance().getStatusBar(myProject).setInfo(message);
    }
  }
}
