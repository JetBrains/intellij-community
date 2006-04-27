package com.intellij.codeInsight.problems;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author cdr
 */
public class MockWolfTheProblemSolver extends WolfTheProblemSolver {
  public static final ProblemUpdateTransaction MOCK_UPDATE_TRANSACTION = new ProblemUpdateTransaction() {
    public void addProblem(Problem problem) {
    }

    public void addProblem(CompilerMessage message) {
    }

    public void commit() {
    }
  };

  public boolean isProblemFile(VirtualFile virtualFile) {
    return false;
  }

  public ProblemUpdateTransaction startUpdatingProblemsInScope(CompileScope compileScope) {
    return MOCK_UPDATE_TRANSACTION;
  }

  public ProblemUpdateTransaction startUpdatingProblemsInScope(VirtualFile virtualFile) {
    return MOCK_UPDATE_TRANSACTION;
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
