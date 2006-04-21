package com.intellij.codeInsight.problems;

import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author cdr
 */
public class MockWolfTheProblemSolver extends WolfTheProblemSolver {
  protected void startCheckingIfVincentSolvedProblemsYet() {

  }

  public void addProblem(Problem problem) {

  }

  public boolean isProblemFile(VirtualFile virtualFile) {
    return false;
  }

  public void addProblemFromCompiler(CompilerMessage message) {

  }

  // serialize all updates to avoid mixing them
  public void startUpdatingProblemsInScope(CompileScope compileScope) {

  }

  public void startUpdatingProblemsInScope(VirtualFile virtualFile) {

  }

  public void finishUpdatingProblems() {

  }

  public Collection<VirtualFile> getProblemFiles() {
    return null;
  }

  public void daemonStopped(boolean toRestartAlarm) {

  }

  public void projectOpened() {

  }

  public void projectClosed() {

  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "mockwolftheproblemsolver";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }
}
