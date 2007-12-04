package com.intellij.openapi.vcs.changes;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class RootsChangedWatcher implements ProjectComponent {
  private Project myProject;
  private MessageBusConnection myConnection;

  public RootsChangedWatcher(Project project) {
    myProject = project;
  }

  public void projectOpened() {
    myConnection = myProject.getMessageBus().connect();
    myConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
          }
        }, ModalityState.NON_MODAL);
      }
    });
  }

  public void projectClosed() {
    myConnection.disconnect();
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "RootsChangedWatcher";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}