package com.intellij.lifecycle;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Pair;

import java.util.ArrayList;
import java.util.List;

public class PeriodicalTasksCloser implements ProjectManagerListener {
  private final List<Pair<String, Runnable>> myInterrupters;

  private PeriodicalTasksCloser(final Project project, final ProjectManager projectManager) {
    myInterrupters = new ArrayList<Pair<String, Runnable>>();
    projectManager.addProjectManagerListener(project, this);
  }

  public static PeriodicalTasksCloser getInstance(final Project project) {
    return ServiceManager.getService(project, PeriodicalTasksCloser.class);
  }

  public void register(final String name, final Runnable runnable) {
    myInterrupters.add(new Pair<String, Runnable>(name, runnable));
  }       

  public void projectOpened(Project project) {
  }

  public boolean canCloseProject(Project project) {
    return true;
  }

  public void projectClosed(Project project) {
  }

  public void projectClosing(Project project) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        for (Pair<String, Runnable> pair : myInterrupters) {
          if (indicator != null) {
            indicator.setText(pair.getFirst());
            indicator.checkCanceled();
          }
          pair.getSecond().run();
        }
      }
    }, "Please wait for safe shutdown of periodical tasks...", true, project);
  }
}
