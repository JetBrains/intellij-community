package com.intellij.codeInsight.problems;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class MockWolfTheProblemSolver extends WolfTheProblemSolver {
  private WolfTheProblemSolver myDelegate;

  public boolean isProblemFile(VirtualFile virtualFile) {
    return myDelegate != null && myDelegate.isProblemFile(virtualFile);
  }

  public void weHaveGotProblem(Problem problem) {
    if (myDelegate != null) myDelegate.weHaveGotProblem(problem);
  }

  public boolean hasProblemFilesBeneath(ProjectViewNode scope) {
    return false;
  }

  public boolean hasProblemFilesBeneath(PsiElement scope) {
    return false;
  }

  public boolean hasProblemFilesBeneath(Module scope) {
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

  public void clearProblems(@NotNull VirtualFile virtualFile) {
    if (myDelegate != null) myDelegate.clearProblems(virtualFile);
  }

  public Problem convertToProblem(CompilerMessage message) {
    return myDelegate == null ? null : myDelegate.convertToProblem(message);
  }

  public Problem convertToProblem(VirtualFile virtualFile, int line, int column, String[] message) {
    return myDelegate == null ? null : myDelegate.convertToProblem(virtualFile, line, column, message);
  }

  public void setDelegate(final WolfTheProblemSolver delegate) {
    myDelegate = delegate;
  }
}
