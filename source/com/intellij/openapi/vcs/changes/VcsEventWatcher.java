package com.intellij.openapi.vcs.changes;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class VcsEventWatcher implements ProjectComponent {
  private Project myProject;
  private MessageBusConnection myConnection;
  private WolfTheProblemSolver.ProblemListener myProblemListener = new MyProblemListener();

  public VcsEventWatcher(Project project) {
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
            if (myProject.isDisposed() || DirectoryIndex.getInstance(myProject).isInitialized()) return;
            VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
          }
        }, ModalityState.NON_MODAL);
      }
    });
    WolfTheProblemSolver.getInstance(myProject).addProblemListener(myProblemListener);
  }

  public void projectClosed() {
    WolfTheProblemSolver.getInstance(myProject).removeProblemListener(myProblemListener);
    myConnection.disconnect();
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "VcsEventWatcher";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private class MyProblemListener extends WolfTheProblemSolver.ProblemListener {
    @Override
    public void problemsAppeared(final VirtualFile file) {
      ChangesViewManager.getInstance(myProject).refreshChangesViewNodeAsync(file);
    }

    @Override
    public void problemsDisappeared(VirtualFile file) {
      ChangesViewManager.getInstance(myProject).refreshChangesViewNodeAsync(file);
    }
  }
}