package com.intellij.codeInsight.problems;

import com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.compiler.impl.FileSetCompileScope;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.nodes.PackageUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import com.intellij.util.Alarm;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author cdr
 */
public class WolfTheProblemSolverImpl extends WolfTheProblemSolver {
  private final Map<VirtualFile, Collection<ProblemImpl>> myProblems = new THashMap<VirtualFile, Collection<ProblemImpl>>();
  private final Alarm myHighlightingAlarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD);
  private final Runnable myRehighlightRequest = new Runnable() {
    public void run() {
      ApplicationManager.getApplication().runReadAction(new Runnable(){
        public void run() {
          startCheckingIfVincentSolvedProblemsYet();
        }
      });
    }
  };
  private final Project myProject;
  private ProjectFileIndex myProjectFileIndex;
  private final ProgressIndicator myProgress;
  private final List<ProblemListener> myProblemListeners = new CopyOnWriteArrayList<ProblemListener>();
  private final ProblemListener fireProblemListeners = new ProblemListener() {
    public void problemsChanged(Collection<VirtualFile> added, Collection<VirtualFile> removed) {
      for (ProblemListener problemListener : myProblemListeners) {
        problemListener.problemsChanged(added, removed);
      }
    }
  };

  private boolean myDaemonStopped;
  private long myPsiModificationCount = -1000;
  private class UpdateImpl implements ProblemUpdateTransaction {
    private final Map<VirtualFile, Collection<ProblemImpl>> backedProblems = new THashMap<VirtualFile, Collection<ProblemImpl>>();
    private final Map<VirtualFile, Collection<ProblemImpl>> problems = new THashMap<VirtualFile, Collection<ProblemImpl>>();
    private final CompileScope scope;

    public UpdateImpl(final Map<VirtualFile, Collection<ProblemImpl>> problems, CompileScope compileScope) {
      scope = compileScope;
      backedProblems.putAll(problems);
    }

    public synchronized void addProblem(Problem problem) {
      VirtualFile virtualFile = problem.getVirtualFile();
      Collection<ProblemImpl> storedProblems = problems.get(virtualFile);
      if (storedProblems == null) {
        storedProblems = new SmartList<ProblemImpl>();
        problems.put(virtualFile, storedProblems);
      }
      storedProblems.add((ProblemImpl)problem);
    }

    public void addProblem(final CompilerMessage message) {
      if (message.getCategory() != CompilerMessageCategory.ERROR) return;
      ApplicationManager.getApplication().runReadAction(new Runnable(){
        public void run() {
          ProblemImpl problem = convertToProblem(message);
          if (problem != null) {
            addProblem(problem);
          }
        }
      });
    }

    public void commit() {
      Set<VirtualFile> added = new THashSet<VirtualFile>();
      added.addAll(problems.keySet());
      added.removeAll(backedProblems.keySet());

      Set<VirtualFile> removed = new THashSet<VirtualFile>();
      for (VirtualFile oldFile : backedProblems.keySet()) {
        if (scope.belongs(oldFile.getUrl())) {
          removed.add(oldFile);
        }
      }
      removed.removeAll(problems.keySet());

      synchronized(myProblems) {
        for (VirtualFile addedFile : added) {
          Collection<ProblemImpl> addedProblems = problems.get(addedFile);
          Collection<ProblemImpl> existingProblems = myProblems.get(addedFile);
          if (existingProblems == null) {
            myProblems.put(addedFile, addedProblems);
          }
          else {
            existingProblems.addAll(addedProblems);
          }
        }
        for (VirtualFile removedFile : removed) {
          myProblems.remove(removedFile);
        }
      }
      if (!added.isEmpty() || !removed.isEmpty()) {
        fireProblemListeners.problemsChanged(added, removed);
      }
      problems.clear();
    }
  }

  public WolfTheProblemSolverImpl(Project project) {
    myProject = project;
    myProgress = new ProgressIndicatorBase() {
      public boolean isCanceled() {
        return super.isCanceled() || myDaemonStopped || !myProject.isOpen();
      }

      public String toString() {
        return "Progress: canceled="+isCanceled()+"; mycanceled="+myDaemonStopped;
      }
    };
  }

  public void projectOpened() {
    myProjectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
  }

  public void projectClosed() {

  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "Problems";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  protected void startCheckingIfVincentSolvedProblemsYet() {
    long psiModificationCount = PsiManager.getInstance(myProject).getModificationTracker().getModificationCount();
    if (psiModificationCount == myPsiModificationCount) return; //optimization
    myPsiModificationCount = psiModificationCount;
    if (!myProject.isOpen()) return;
    myDaemonStopped = false;
    List<VirtualFile> toCheck;
    synchronized(myProblems) {
      toCheck = new ArrayList<VirtualFile>(myProblems.keySet());
    }
    try {
      for (VirtualFile virtualFile : toCheck) {
        if (myProgress.isCanceled()) break;
        if (virtualFile.isValid()) {
          orderVincentToCleanTheCar(virtualFile);
        }
        else {
          synchronized(myProblems) {
            myProblems.remove(virtualFile);
          }
        }
      }
    }
    catch (ProcessCanceledException e) {
      // ignore
    }
  }

  private void orderVincentToCleanTheCar(final VirtualFile file) throws ProcessCanceledException {
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (willBeHighlightedAnyway(file)) return;
    final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (psiFile == null) return;
    ProgressManager.getInstance().runProcess(new Runnable(){
      public void run() {
        if (!myProject.isOpen()) return;
        StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
        try {
          if (statusBar != null) {
            statusBar.setInfo("Checking '"+file.getPresentableUrl()+"'");
          }
          GeneralHighlightingPass pass = new GeneralHighlightingPass(myProject, psiFile, document, 0, document.getTextLength(), false, true);
          pass.doCollectInformation(myProgress);
        }
        finally {
          if (statusBar != null) {
            statusBar.setInfo("");
          }
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

  public void daemonStopped(final boolean toRestartAlarm) {
    myDaemonStopped = true;
    if (toRestartAlarm) {
      restartHighlighting();
    }
  }

  public boolean hasProblemFilesBeneath(final ProjectViewNode scope) {
    return hasProblemFilesBeneath(new Condition<VirtualFile>() {
      public boolean value(final VirtualFile virtualFile) {
        return scope.contains(virtualFile);
      }
    });
  }

  private boolean hasProblemFilesBeneath(Condition<VirtualFile> condition){
    synchronized (myProblems) {
      Set<VirtualFile> problemFiles = myProblems.keySet();
      for (VirtualFile problemFile : problemFiles) {
        if (condition.value(problemFile)) return true;
      }
      return false;
    }
  }

  public boolean hasProblemFilesBeneath(final PsiElement scope) {
    return hasProblemFilesBeneath(new Condition<VirtualFile>(){
      public boolean value(final VirtualFile virtualFile) {
        if (scope instanceof PsiDirectory) {
          final PsiDirectory directory = (PsiDirectory)scope;
          return VfsUtil.isAncestor(directory.getVirtualFile(), virtualFile, false);
        } else if (scope instanceof PsiPackage){
          final PsiDirectory[] psiDirectories = ((PsiPackage)scope).getDirectories();
          for (PsiDirectory directory : psiDirectories) {
            if (VfsUtil.isAncestor(directory.getVirtualFile(), virtualFile, false)) {
              return true;
            }
          }
        }
        return false;
      }
    });
  }

  public boolean hasProblemFilesBeneath(final Module scope) {
    return hasProblemFilesBeneath(new Condition<VirtualFile>() {
      public boolean value(final VirtualFile virtualFile) {
        return PackageUtil.moduleContainsFile(scope, virtualFile, false);
      }
    });
  }

  public void addProblemListener(ProblemListener listener) {
    myProblemListeners.add(listener);
  }

  public void removeProblemListener(ProblemListener listener) {
    myProblemListeners.remove(listener);
  }

  private void restartHighlighting() {
    myHighlightingAlarm.cancelAllRequests();
    myHighlightingAlarm.addRequest(myRehighlightRequest, 200);
  }

  public boolean isProblemFile(VirtualFile virtualFile) {
    synchronized (myProblems) {
      return myProblems.containsKey(virtualFile);
    }
  }

  private static ProblemImpl convertToProblem(final CompilerMessage message) {
    VirtualFile virtualFile = message.getVirtualFile();
    if (virtualFile == null) {
      Navigatable navigatable = message.getNavigatable();
      if (navigatable instanceof OpenFileDescriptor) {
        virtualFile = ((OpenFileDescriptor)navigatable).getFile();
      }
    }
    if (virtualFile == null) return null;
    HighlightInfo info = HighlightInfo.createHighlightInfo(convertToHighlightInfoType(message), getTextRange(message), message.getMessage());
    return new ProblemImpl(virtualFile, info);
  }

  private static TextRange getTextRange(final CompilerMessage message) {
    Navigatable navigatable = message.getNavigatable();
    if (navigatable instanceof OpenFileDescriptor) {
      int offset = ((OpenFileDescriptor)navigatable).getOffset();
      return new TextRange(offset, offset);
    }
    return new TextRange(0,0);
  }

  private static HighlightInfoType convertToHighlightInfoType(final CompilerMessage message) {
    CompilerMessageCategory category = message.getCategory();
    switch(category) {
      case ERROR: return HighlightInfoType.ERROR;
      case WARNING: return HighlightInfoType.WARNING;
      case INFORMATION: return HighlightInfoType.INFORMATION;
      case STATISTICS: return HighlightInfoType.INFORMATION;
    }
    return null;
  }

  public ProblemUpdateTransaction startUpdatingProblemsInScope(final CompileScope compileScope) {
    synchronized (myProblems) {
      return new UpdateImpl(myProblems, compileScope);
    }
  }

  public ProblemUpdateTransaction startUpdatingProblemsInScope(final VirtualFile virtualFile) {
    Module module = myProjectFileIndex.getModuleForFile(virtualFile);
    CompileScope compileScope = new FileSetCompileScope(new VirtualFile[]{virtualFile}, new Module[]{module});
    return startUpdatingProblemsInScope(compileScope);
  }
}
