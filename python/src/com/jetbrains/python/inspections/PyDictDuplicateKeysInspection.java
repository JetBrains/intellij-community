package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

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

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      if (isDict(node)) {
        HashSet<String> set = new HashSet<String>();
        PyExpression[] argumentList = node.getArgumentList().getArguments();
        for (PyExpression argument : argumentList) {
          if (argument instanceof PyParenthesizedExpression)
            argument = ((PyParenthesizedExpression)argument).getContainedExpression();
          if (argument instanceof PySequenceExpression) {
            for (PyElement el : ((PySequenceExpression)argument).getElements()) {
              String key = getKey(el);
              checkKey(key, set, node);
            }
          }
          else {
            String key = getKey(argument);
            checkKey(key, set, node);
          }
        }
      }
    }

    private void checkKey(String key, Set<String> set, PyCallExpression node) {
      if (key != null) {
        if (set.contains(key)) {
          registerProblem(node, "Dictionary contains duplicate keys " + key);
        }
        set.add(key);
      }
    }

    @Nullable
    private String getKey(PyElement argument) {
      if (argument instanceof PyParenthesizedExpression) {
        PyExpression expr = ((PyParenthesizedExpression)argument).getContainedExpression();
        if (expr instanceof PyTupleExpression) {
          PyElement key = ((PyTupleExpression)expr).getElements()[0];
          if (key instanceof PyStringLiteralExpression) {
            return ((PyStringLiteralExpression)key).getStringValue();
          }
          else {
            return key.getText();
          }
        }
      }
      if (argument instanceof PyKeywordArgument) {
        return ((PyKeywordArgument)argument).getKeyword();
      }
      return null;
    }

    private boolean isDict(PyCallExpression expression) {
      String name = expression.getCallee().getText();
      if ("dict".equals(name)) {
        return true;
      }
      return false;
    }

  }
}
