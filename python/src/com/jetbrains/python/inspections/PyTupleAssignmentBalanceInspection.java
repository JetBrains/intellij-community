package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 27.02.2010
 * Time: 12:55:26
 */
public class PyTupleAssignmentBalanceInspection extends LocalInspectionTool {
  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.incorrect.assignment");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "PyTupleAssignmentBalanceInspection";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  private static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
      PyExpression lhsExpression = node.getLeftHandSideExpression();
      PyExpression assignedValue = node.getAssignedValue();
      if (lhsExpression instanceof PyTupleExpression && assignedValue instanceof PyTupleExpression) {
        int valuesLength = ((PyTupleExpression)assignedValue).getElements().length;
        PyExpression[] elements = ((PyTupleExpression) lhsExpression).getElements();
        boolean containsStarExpression = false;
        VirtualFile virtualFile = node.getContainingFile().getVirtualFile();
        if (virtualFile != null && LanguageLevel.forFile(virtualFile).isPy3K()) {
          for (PyExpression target: elements) {
            if (target instanceof PyStarExpression) {
              containsStarExpression = true;
              ++valuesLength;
            }
          }
        }

        int targetsLength = elements.length;
        if (targetsLength > valuesLength) {
          registerProblem(assignedValue, "Need more values to unpack");
        } else if (!containsStarExpression && targetsLength < valuesLength) {
          registerProblem(assignedValue, "Too many values to unpack");
        }
      }
    }
  }
}
