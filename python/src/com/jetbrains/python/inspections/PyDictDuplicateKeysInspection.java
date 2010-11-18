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
 *
 * Inspection to detect using the same value as dictionary key twice.
 */
public class PyDictDuplicateKeysInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.duplicate.keys");
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
    public void visitPyDictLiteralExpression(PyDictLiteralExpression node) {
      if (node.getElements().length != 0){
        HashSet<String> set = new HashSet<String>();
        for (PyExpression exp : node.getElements()) {
          PyExpression key = ((PyKeyValueExpression)exp).getKey();
          if (key instanceof PyNumericLiteralExpression
                  || key instanceof PyStringLiteralExpression || key instanceof PyReferenceExpression) {
            if (set.contains(key.getText())) {
              registerProblem(node, "Dictionary contains duplicate keys " + key.getText());
            }
            set.add(key.getText());
          }
        }
      }
    }
  }
}
