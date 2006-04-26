package com.intellij.codeInsight.problems;

import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.problems.Problem;
import com.intellij.ide.projectView.ProjectViewNode;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author cdr
 */
public class MockWolfTheProblemSolver extends WolfTheProblemSolver {

  public boolean isProblemFile(VirtualFile virtualFile) {
    return false;
  }

  public ProblemUpdateTransaction startUpdatingProblemsInScope(CompileScope compileScope) {
    return null;
  }

  public ProblemUpdateTransaction startUpdatingProblemsInScope(VirtualFile virtualFile) {
    return new ProblemUpdateTransaction() {
      public void addProblem(Problem problem) {

      }

      public void addProblem(CompilerMessage message) {

      }

      public void commit() {

      }
    };
  }

  public Collection<VirtualFile> getProblemFiles() {
    return null;
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
}
