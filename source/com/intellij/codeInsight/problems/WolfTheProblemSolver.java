package com.intellij.codeInsight.problems;

import com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Alarm;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author cdr
 */
public class WolfTheProblemSolver implements ProjectComponent {
  private final Collection<VirtualFile> myProblemFiles = new THashSet<VirtualFile>();
  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD);
  private final Runnable myCheckingRequest = new Runnable() {
    public void run() {
      ApplicationManager.getApplication().runReadAction(new Runnable(){
        public void run() {
          startCheckingIfVincentSolvedProblemsYet();
        }
      });
    }
  };
  private final Project myProject;
  private final ProgressIndicator myProgress;
  private boolean myDaemonStopped;
  private long myPsiModificationCount;
  private boolean myRestartOnBecomeVisible;
  private boolean myProblemsAreVisible;

  public static WolfTheProblemSolver getInstance(Project project) {
    return project.getComponent(WolfTheProblemSolver.class);
  }

  public WolfTheProblemSolver(Project project) {
    myProject = project;
    myProgress = new ProgressIndicatorBase(){
      public boolean isCanceled() {
        return super.isCanceled() || myDaemonStopped;
      }

      public String toString() {
        return "Progress: canceled="+isCanceled()+"; mycanceled="+myDaemonStopped;
      }
    };
  }

  public void projectOpened() {
    ToolWindowManagerEx toolWindowManagerEx = ToolWindowManagerEx.getInstanceEx(myProject);
    if (toolWindowManagerEx != null) { //in tests ?
      toolWindowManagerEx.addToolWindowManagerListener(new ToolWindowManagerAdapter(){
        public void stateChanged() {
          ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
          if (window == null) return;
          synchronized (WolfTheProblemSolver.this) {
            boolean visible = window.isVisible();
            if (myProblemsAreVisible != visible) {
              if (visible && myRestartOnBecomeVisible) {
                restartChecking();
                myRestartOnBecomeVisible = false;
              }
              myProblemsAreVisible = visible;
            }
          }
        }
      });
    }
  }

  public void projectClosed() {

  }

  @NonNls
  public String getComponentName() {
    return "Problems";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  private void startCheckingIfVincentSolvedProblemsYet() {
    synchronized (this) {
      if (!myProblemsAreVisible) {
        myRestartOnBecomeVisible = true;
        return; //optimization
      }
    }

    long psiModificationCount = PsiManager.getInstance(myProject).getModificationTracker().getModificationCount();
    if (psiModificationCount == myPsiModificationCount) return; //optimization
    myPsiModificationCount = psiModificationCount;

    myDaemonStopped = false;
    List<VirtualFile> toCheck;
    synchronized(myProblemFiles) {
      toCheck = new ArrayList<VirtualFile>(myProblemFiles);
    }
    try {
      for (VirtualFile virtualFile : toCheck) {
        if (myProgress.isCanceled()) break;
        orderVincentToCleanCar(virtualFile);
      }
    }
    catch (ProcessCanceledException e) {
      // ignore
    }
  }

  private void orderVincentToCleanCar(final VirtualFile file) throws ProcessCanceledException {
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (willBeHighlightedAnyway(file)) return;
    final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (psiFile == null) return;
    ProgressManager.getInstance().runProcess(new Runnable(){
      public void run() {
        try {
          ProblemsToolWindow.getInstance(myProject).setProgressText("Checking '"+file.getPresentableUrl()+"'");
          GeneralHighlightingPass pass = new GeneralHighlightingPass(myProject, psiFile, document, 0, document.getTextLength(), false, true);
          pass.doCollectInformation(myProgress);
        }
        finally {
          ProblemsToolWindow.getInstance(myProject).setProgressText("");
        }
      }
    },myProgress);
  }

  private boolean willBeHighlightedAnyway(final VirtualFile file) {
    // opened in some editor, and hence will be highlighted automatically sometime later
    FileEditor[] selectedEditors = FileEditorManager.getInstance(myProject).getSelectedEditors();
    for (FileEditor editor : selectedEditors) {
      if (!(editor instanceof TextEditor)) continue;
      Document document = ((TextEditor)editor).getEditor().getDocument();
      PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getCachedPsiFile(document);
      if (psiFile == null) continue;
      if (file == psiFile.getVirtualFile()) return true;
    }
    return false;
  }

  public void removeProblemFile(final VirtualFile virtualFile) {
    synchronized(myProblemFiles) {
      myProblemFiles.remove(virtualFile);
    }
  }

  public void addProblemFile(final VirtualFile virtualFile) {
    if (virtualFile != null) {
      synchronized(myProblemFiles) {
        myProblemFiles.add(virtualFile);
      }
    }
  }

  public void daemonStopped(final boolean toRestartAlarm) {
    myDaemonStopped = true;
    if (toRestartAlarm) {
      restartChecking();
    }
  }

  private void restartChecking() {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(myCheckingRequest, 200);
  }

  public void clearProblemFiles() {
    synchronized (myProblemFiles) {
      myProblemFiles.clear();
    }
  }
}
