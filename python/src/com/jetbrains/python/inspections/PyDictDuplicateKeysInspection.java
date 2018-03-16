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
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.quickfix.PyRemoveDictKeyQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.HashMap;
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
    public void visitPyDictLiteralExpression(@NotNull PyDictLiteralExpression node) {
      if (node.isEmpty()) return;

      final Map<String, PsiElement> map = new HashMap<>();
      for (PyKeyValueExpression element : node.getElements()) {
        checkKey(map, getDictLiteralKey(element), new PyRemoveDictKeyQuickFix());
      }
    }

    @Override
    public void visitPyCallExpression(@NotNull PyCallExpression node) {
      if (!isDict(node)) return;

      final Map<String, PsiElement> map = new HashMap<>();
      for (PyExpression argument : node.getArguments()) {
        argument = PyPsiUtils.flattenParens(argument);

        if (argument instanceof PySequenceExpression) {
          for (PyExpression element : ((PySequenceExpression)argument).getElements()) {
            checkKey(map, getDictCallKey(element));
          }
        }
        else if (argument instanceof PyKeywordArgument) {
          checkKey(map, getDictCallKey(argument));
        }
      }
    }

    @Nullable
    private Pair<PsiElement, String> getDictLiteralKey(@NotNull PyKeyValueExpression argument) {
      final PyExpression key = argument.getKey();
      final String keyValue = getKeyValue(key);
      return keyValue != null ? Pair.createNonNull(key, keyValue) : null;
    }

    @Nullable
    private String getKeyValue(@NotNull PsiElement node) {
      if (node instanceof PyStringLiteralExpression) {
        return ((PyStringLiteralExpression)node).getStringValue();
      }

      if (node instanceof PyNumericLiteralExpression) {
        final BigDecimal value = ((PyNumericLiteralExpression)node).getBigDecimalValue();
        if (value != null) {
          final String keyValue = value.toPlainString();
          return !value.equals(BigDecimal.ZERO) &&
                 myTypeEvalContext.getType((PyNumericLiteralExpression)node) == PyBuiltinCache.getInstance(node).getComplexType()
                 ? keyValue + "j"
                 : keyValue;
        }
      }

      return node instanceof PyLiteralExpression || node instanceof PyReferenceExpression ? node.getText() : null;
    }

    private void checkKey(@NotNull Map<String, PsiElement> map,
                          @Nullable Pair<PsiElement, String> keyAndValue,
                          @NotNull LocalQuickFix... quickFixes) {
      if (keyAndValue == null) return;

      final PsiElement key = keyAndValue.first;
      final String keyValue = keyAndValue.second;

      if (map.containsKey(keyValue)) {
        registerProblem(key, "Dictionary contains duplicate keys '" + keyValue + "'", quickFixes);
        registerProblem(map.get(keyValue), "Dictionary contains duplicate keys '" + keyValue + "'", quickFixes);
      }
      map.put(keyValue, key);
    }

    @Nullable
    private Pair<PsiElement, String> getDictCallKey(@Nullable PyExpression argument) {
      if (argument instanceof PyParenthesizedExpression) {
        final PyExpression expression = PyPsiUtils.flattenParens(argument);
        if (expression instanceof PyTupleExpression) {
          final PyExpression key = ((PyTupleExpression)expression).getElements()[0];
          final String keyValue = getKeyValue(key);
          if (keyValue != null) {
            return Pair.createNonNull(key, keyValue);
          }
        }
      }

      if (argument instanceof PyKeywordArgument) {
        final ASTNode node = ((PyKeywordArgument)argument).getKeywordNode();
        final String keyValue = ((PyKeywordArgument)argument).getKeyword();
        if (node != null && keyValue != null) {
          return Pair.createNonNull(node.getPsi(), keyValue);
        }
      }

      return null;
    }

    private static boolean isDict(@NotNull PyCallExpression expression) {
      final PyExpression callee = expression.getCallee();
      return callee != null && "dict".equals(callee.getText());
    }
  }
}
