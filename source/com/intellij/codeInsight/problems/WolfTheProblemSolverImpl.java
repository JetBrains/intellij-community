package com.intellij.codeInsight.problems;

import com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.compiler.impl.FileSetCompileScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Alarm;
import com.intellij.util.SmartList;
import com.intellij.lang.annotation.HighlightSeverity;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author cdr
 */
public class WolfTheProblemSolverImpl extends WolfTheProblemSolver {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.problems.WolfTheProblemSolver");

  private Map<VirtualFile, Collection<Problem>> myProblems = new THashMap<VirtualFile, Collection<Problem>>();
  private final Alarm myHighlightingAlarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD);
  private final Alarm myUpdateProblemsAlarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD);
  private final Runnable myRehighlightRequest = new Runnable() {
    public void run() {
      ApplicationManager.getApplication().runReadAction(new Runnable(){
        public void run() {
          //startCheckingIfVincentSolvedProblemsYet();
        }
      });
    }
  };
  private final Project myProject;
  private ProjectFileIndex myProjectFileIndex;
  private final ProgressIndicator myProgress;
  private boolean myDaemonStopped;
  private long myPsiModificationCount;
  private CompileScope myCompileScope;
  private Map<VirtualFile, Collection<Problem>> myBackedProblems;

  public WolfTheProblemSolverImpl(Project project) {
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

    myDaemonStopped = false;
    List<VirtualFile> toCheck;
    synchronized(myProblems) {
      toCheck = new ArrayList<VirtualFile>(myProblems.keySet());
    }
    try {
      for (VirtualFile virtualFile : toCheck) {
        if (myProgress.isCanceled()) break;
        orderVincentToCleanTheCar(virtualFile);
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
        try {
          //ProblemsToolWindow.getInstance(myProject).setProgressText("Checking '"+file.getPresentableUrl()+"'");
          GeneralHighlightingPass pass = new GeneralHighlightingPass(myProject, psiFile, document, 0, document.getTextLength(), false, true);
          pass.doCollectInformation(myProgress);
        }
        finally {
          //ProblemsToolWindow.getInstance(myProject).setProgressText("");
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

  public void addProblem(final Problem problem) {
    if (problem.highlightInfo.getSeverity() != HighlightSeverity.ERROR) return;
    myUpdateProblemsAlarm.addRequest(new Runnable() {
      public void run() {
        if (problem.virtualFile != null) {
          synchronized (myProblems) {
            Collection<Problem> problems = myProblems.get(problem.virtualFile);
            if (problems == null) {
              problems = new SmartList<Problem>();
              myProblems.put(problem.virtualFile, problems);
            }
            problems.add(problem);
          }
        }
      }
    }, 0);
  }

  public void daemonStopped(final boolean toRestartAlarm) {
    myDaemonStopped = true;
    if (toRestartAlarm) {
      restartHighlighting();
    }
  }

  private void restartHighlighting() {
    myHighlightingAlarm.cancelAllRequests();
    myHighlightingAlarm.addRequest(myRehighlightRequest, 200);
  }

  public boolean isProblemFile(VirtualFile virtualFile) {
    synchronized (myProblems) {
      Set<VirtualFile> actualProblems = myBackedProblems == null ? myProblems.keySet() : myBackedProblems.keySet();
      return actualProblems.contains(virtualFile);
    }
  }

  public void addProblemFromCompiler(final CompilerMessage message) {
    if (message.getCategory() != CompilerMessageCategory.ERROR) return;
    VirtualFile virtualFile = message.getVirtualFile();
    if (virtualFile == null) {
      Navigatable navigatable = message.getNavigatable();
      if (navigatable instanceof OpenFileDescriptor) {
        virtualFile = ((OpenFileDescriptor)navigatable).getFile();
      }
    }
    LOG.assertTrue(virtualFile != null);
    HighlightInfo info = HighlightInfo.createHighlightInfo(convertToHighlightInfoType(message), getTextRange(message), message.getMessage());
    addProblem(new Problem(virtualFile, info));
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


  // serialize all updates to avoid mixing them
  public void startUpdatingProblemsInScope(final CompileScope compileScope) {
    myUpdateProblemsAlarm.addRequest(new Runnable() {
      public void run() {
        myCompileScope = compileScope;

        synchronized (myProblems) {
          myBackedProblems = myProblems;
          myProblems = new THashMap<VirtualFile, Collection<Problem>>();
        }
      }
    }, 0);
  }
  public void startUpdatingProblemsInScope(final VirtualFile virtualFile) {
    Module module = myProjectFileIndex.getModuleForFile(virtualFile);
    CompileScope compileScope = new FileSetCompileScope(new VirtualFile[]{virtualFile}, new Module[]{module});
    startUpdatingProblemsInScope(compileScope);
  }

  public void finishUpdatingProblems() {
    myUpdateProblemsAlarm.addRequest(new Runnable() {
      public void run() {
        // remove not added problems
        for (VirtualFile file : myBackedProblems.keySet()) {
          Collection<Problem> problems = myBackedProblems.get(file);
          for (Problem problem : problems) {
            if (!isProblemInScope(problem, myCompileScope)) {
              addProblem(problem);
            }
          }
        }
        //todo report added/removed problems here
        Set<VirtualFile> added = new THashSet<VirtualFile>();
        added.addAll(myProblems.keySet());
        added.removeAll(myBackedProblems.keySet());

        Set<VirtualFile> removed = new THashSet<VirtualFile>();
        removed.addAll(myBackedProblems.keySet());
        removed.removeAll(myProblems.keySet());
        new ProblemsUpdater(myProject).updateProblems(added, removed);

        myBackedProblems = null;
        myCompileScope = null;
      }
    }, 0);
  }

  private static boolean isProblemInScope(final Problem problem, final CompileScope compileScope) {
    return compileScope.belongs(problem.virtualFile.getUrl());
  }

  public Collection<VirtualFile> getProblemFiles() {
    return myProblems.keySet();
  }
}
