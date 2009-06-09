package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Detects code following 'return', 'break', 'continue', and 'raise'.
 */
public class PyUnreachableCodeInspection extends LocalInspectionTool {
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.unreachable.code");
  }

  @NotNull
  public String getShortName() {
    return "PyUnreachableCodeInspection";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true; // else profile won't allow it
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new Visitor(holder);
  }

  public static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    private void markFollowingStatementAsWrong(PsiElement node) {
      //if (node.getParent() instanceof PyStatementList) { // check just in case?
      PsiElement first_after_us = PyUtil.getFirstNonCommentAfter(node.getNextSibling());
      if (first_after_us != null) {
        getHolder().registerProblem(
          first_after_us, PyBundle.message("INSP.unreachable.code"),
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        );
      }
    }

    @Override
    public void visitPyReturnStatement(PyReturnStatement node) {
      super.visitPyElement(node);
      markFollowingStatementAsWrong(node);
    }

    @Override
    public void visitPyRaiseStatement(PyRaiseStatement node) {
      markFollowingStatementAsWrong(node);
    }

    @Override
    public void visitPyBreakStatement(PyBreakStatement node) {
      markFollowingStatementAsWrong(node);
    }

    @Override
    public void visitPyContinueStatement(PyContinueStatement node) {
      markFollowingStatementAsWrong(node);
    }


  }
}
