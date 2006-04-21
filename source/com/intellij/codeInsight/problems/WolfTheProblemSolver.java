package com.intellij.codeInsight.problems;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompileScope;

import java.util.Collection;

/**
 * @author cdr
 */
public abstract class WolfTheProblemSolver implements ProjectComponent {
  public static WolfTheProblemSolver getInstance(Project project) {
    return project.getComponent(WolfTheProblemSolver.class);
  }

  protected abstract void startCheckingIfVincentSolvedProblemsYet();

  public abstract void addProblem(Problem problem);

  public abstract boolean isProblemFile(VirtualFile virtualFile);

  public abstract void addProblemFromCompiler(CompilerMessage message);

  // serialize all updates to avoid mixing them
  public abstract void startUpdatingProblemsInScope(CompileScope compileScope);

  public abstract void startUpdatingProblemsInScope(VirtualFile virtualFile);

  public abstract void finishUpdatingProblems();

  public abstract Collection<VirtualFile> getProblemFiles();

  public abstract void daemonStopped(boolean toRestartAlarm);
}
