package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.DictCreationQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User: catherine
 */
public class PyDictDuplicateKeysInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Dictionary contains duplicate keys";
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
      if (node.getAssignedValue() instanceof PyDictLiteralExpression) {
        PyDictLiteralExpression expression = (PyDictLiteralExpression)node.getAssignedValue();
        if (expression.getElements().length != 0){
          HashSet<String> set = new HashSet<String>();
          for (PyExpression exp : expression.getElements()) {
            PyExpression key = ((PyKeyValueExpression)exp).getKey();
            if (set.contains(key.getText())) {
              registerProblem(node, "Dictionary contains duplicate keys " + key.getText());
              break;
            }
            set.add(key.getText());
          }
        }
      }
    }

  }
}
