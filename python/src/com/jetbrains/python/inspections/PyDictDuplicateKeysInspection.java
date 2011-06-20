package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

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
        Map<String, PyElement> map = new HashMap<String, PyElement>();
        for (PyExpression exp : node.getElements()) {
          PyExpression key = ((PyKeyValueExpression)exp).getKey();
          if (key instanceof PyNumericLiteralExpression
                  || key instanceof PyStringLiteralExpression || key instanceof PyReferenceExpression) {
            if (map.keySet().contains(key.getText())) {
              registerProblem(key, "Dictionary contains duplicate keys " + key.getText());
              registerProblem(map.get(key.getText()), "Dictionary contains duplicate keys " + key.getText());
            }
            map.put(key.getText(), key);
          }
        }
      }
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      if (isDict(node)) {
        Map<String, PsiElement> map = new HashMap<String, PsiElement>();
        PyExpression[] argumentList = node.getArgumentList().getArguments();
        for (PyExpression argument : argumentList) {
          if (argument instanceof PyParenthesizedExpression)
            argument = ((PyParenthesizedExpression)argument).getContainedExpression();
          if (argument instanceof PySequenceExpression) {
            for (PyElement el : ((PySequenceExpression)argument).getElements()) {
              PsiElement key = getKey(el);
              checkKey(map, key);
            }
          }
          else {
            PsiElement key = getKey(argument);
            checkKey(map, key);
          }
        }
      }
    }

    private void checkKey(Map<String, PsiElement> map, PsiElement node) {
      if (node == null) return;
      String key = node.getText();
      if (node instanceof PyStringLiteralExpression)
        key = ((PyStringLiteralExpression)node).getStringValue();
      if (map.keySet().contains(key)) {
        registerProblem(node, "Dictionary contains duplicate keys " + key);
        registerProblem(map.get(key), "Dictionary contains duplicate keys " + key);
      }
      map.put(key, node);
    }

    @Nullable
    private PsiElement getKey(PyElement argument) {
      if (argument instanceof PyParenthesizedExpression) {
        PyExpression expr = ((PyParenthesizedExpression)argument).getContainedExpression();
        if (expr instanceof PyTupleExpression) {
          PyElement key = ((PyTupleExpression)expr).getElements()[0];
          return key;
        }
      }
      if (argument instanceof PyKeywordArgument) {
        ASTNode keyWord = ((PyKeywordArgument)argument).getKeywordNode();
        if (keyWord != null) return keyWord.getPsi();
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
