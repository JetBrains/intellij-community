package com.intellij.codeInsight.problems;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class MockWolfTheProblemSolver extends WolfTheProblemSolver {
  private WolfTheProblemSolver myDelegate;

  private final ProblemUpdateTransaction myUpdate = new ProblemUpdateTransaction() {
    public void addProblem(Problem problem) {
    }

    public void addProblem(CompilerMessage message) {
    }

    public void commit() {
    }
  };

  public boolean isProblemFile(VirtualFile virtualFile) {
    return myDelegate != null && myDelegate.isProblemFile(virtualFile);
  }

  public ProblemUpdateTransaction startUpdatingProblemsInScope(CompileScope compileScope) {
    return myDelegate == null ? myUpdate : myDelegate.startUpdatingProblemsInScope(compileScope);
  }

  public ProblemUpdateTransaction startUpdatingProblemsInScope(VirtualFile virtualFile) {
    return myDelegate == null ? myUpdate : myDelegate.startUpdatingProblemsInScope(virtualFile);
  }

  public boolean hasProblemFilesUnder(ProjectViewNode scope) {
    return false;
  }

  public void addProblemListener(ProblemListener listener) {

  }

  public void removeProblemListener(ProblemListener listener) {

  }

  public void projectOpened() {

  }

  public void projectClosed() {

  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "mockwolf";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  public void setDelegate(final WolfTheProblemSolver delegate) {
    myDelegate = delegate;
  }
}
