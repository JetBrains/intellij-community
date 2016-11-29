/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.quickfix.PyRemoveDictKeyQuickFix;
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
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyDictLiteralExpression(PyDictLiteralExpression node) {
      final PyKeyValueExpression[] elements = node.getElements();
      if (elements.length != 0){
        final Map<String, PyElement> map = new HashMap<>();
        for (PyExpression exp : elements) {
          final PyExpression key = ((PyKeyValueExpression)exp).getKey();
          if (key instanceof PyNumericLiteralExpression
                  || key instanceof PyStringLiteralExpression || key instanceof PyReferenceExpression) {
            if (map.keySet().contains(key.getText())) {
              registerProblem(key, "Dictionary contains duplicate keys " + key.getText(), new PyRemoveDictKeyQuickFix());
              registerProblem(map.get(key.getText()), "Dictionary contains duplicate keys " + key.getText(), new PyRemoveDictKeyQuickFix());
            }
            map.put(key.getText(), key);
          }
        }
      }
    }

    @Override
    public void visitPyCallExpression(final PyCallExpression node) {
      if (isDict(node)) {
        final Map<String, PsiElement> map = new HashMap<>();
        final PyArgumentList pyArgumentList = node.getArgumentList();
        if (pyArgumentList == null) return;
        final PyExpression[] arguments = pyArgumentList.getArguments();
        for (PyExpression argument : arguments) {
          if (argument instanceof PyParenthesizedExpression)
            argument = ((PyParenthesizedExpression)argument).getContainedExpression();
          if (argument instanceof PySequenceExpression) {
            for (PyElement el : ((PySequenceExpression)argument).getElements()) {
              final PsiElement key = getKey(el);
              checkKey(map, key);
            }
          }
          else {
            final PsiElement key = getKey(argument);
            checkKey(map, key);
          }
        }
      }
    }

    private void checkKey(final Map<String, PsiElement> map, final PsiElement node) {
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
    private static PsiElement getKey(final PyElement argument) {
      if (argument instanceof PyParenthesizedExpression) {
        final PyExpression expr = ((PyParenthesizedExpression)argument).getContainedExpression();
        if (expr instanceof PyTupleExpression) {
          return ((PyTupleExpression)expr).getElements()[0];
        }
      }
      if (argument instanceof PyKeywordArgument) {
        ASTNode keyWord = ((PyKeywordArgument)argument).getKeywordNode();
        if (keyWord != null) return keyWord.getPsi();
      }
      return null;
    }

    private static boolean isDict(final PyCallExpression expression) {
      final PyExpression callee = expression.getCallee();
      if (callee == null) return false;
      final String name = callee.getText();
      if ("dict".equals(name)) {
        return true;
      }
      return false;
    }

  }
}
