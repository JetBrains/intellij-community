/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;

import javax.swing.*;

public class RefreshProgress extends ProgressIndicatorBase {
  private String myMessage;

  public RefreshProgress(final String message) {
    myMessage = message;
  }

  public void start() {
    super.start();

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (ApplicationManager.getApplication().isDisposed()) return;
        final WindowManager windowManager = WindowManager.getInstance();
        if (windowManager == null) return;

        Project[] projects= ProjectManager.getInstance().getOpenProjects();
        if(projects.length==0){
          projects=new Project[]{null};
        }

        for (Project project : projects) {
          final StatusBarEx statusBar = (StatusBarEx)windowManager.getStatusBar(project);
          if (statusBar == null) continue;

          statusBar.startRefreshIndication(myMessage);
        }
      }
    });

  }

  public void stop() {
    super.stop();

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (ApplicationManager.getApplication().isDisposed()) return;
        final WindowManager windowManager = WindowManager.getInstance();
        if (windowManager == null) return;

        Project[] projects= ProjectManager.getInstance().getOpenProjects();
        if(projects.length==0){
          projects=new Project[]{null};
        }

        for (Project project : projects) {
          final StatusBarEx statusBar = (StatusBarEx)windowManager.getStatusBar(project);
          if (statusBar == null) continue;

          statusBar.stopRefreshIndication();
        }
      }
    });
  }
}