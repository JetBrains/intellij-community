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
package com.jetbrains.python.inspections.quickfix;

import com.google.common.collect.Maps;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.PyDictCreationInspection;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.jetbrains.python.psi.PyUtil.as;

public class DictCreationQuickFix extends PsiUpdateModCommandQuickFix {
  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.dict.creation");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    final Map<String, String> statementsMap = Maps.newLinkedHashMap();
    final PyAssignmentStatement myStatement = as(element, PyAssignmentStatement.class);
    if (myStatement == null) return;
    final PyExpression assignedValue = myStatement.getAssignedValue();
    if (assignedValue instanceof PyDictLiteralExpression) {
      for (PsiElement expression : assignedValue.getChildren()) {
        if (expression instanceof PyKeyValueExpression kvExpr) {
          final PyExpression value = kvExpr.getValue();
          if (value != null) {
            statementsMap.put(kvExpr.getKey().getText(), value.getText());
          }
        }
        else if (expression instanceof PyDoubleStarExpression) {
          statementsMap.put(expression.getText(), null);
        }
      }

      PyStatement statement = PsiTreeUtil.getNextSiblingOfType(myStatement, PyStatement.class);
      while (statement instanceof PyAssignmentStatement assignmentStatement) {
        final PyExpression target = myStatement.getTargets()[0];
        final String targetName = target.getName();
        if (targetName != null) {
          final List<Pair<PyExpression, PyExpression>> targetsToValues =
            PyDictCreationInspection.getDictTargets(target, targetName, assignmentStatement);
          final PyStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PyStatement.class);
          if (targetsToValues == null || targetsToValues.isEmpty()) break;
          for (Pair<PyExpression, PyExpression> targetToValue : targetsToValues) {
            final PySubscriptionExpression subscription = (PySubscriptionExpression)targetToValue.first;
            final PyExpression indexExpression = subscription.getIndexExpression();
            assert indexExpression != null;
            final String indexText;
            if (indexExpression instanceof PyTupleExpression) {
              indexText = "(" + indexExpression.getText() + ")";
            }
            else {
              indexText = indexExpression.getText();
            }

            final String valueText;
            if (targetToValue.second instanceof PyTupleExpression) {
              valueText = "(" + targetToValue.second.getText() + ")";
            }
            else {
              valueText = targetToValue.second.getText();
            }

            statementsMap.put(indexText, valueText);
            statement.delete();
          }
          statement = nextStatement;
        }
      }
      List<String> statements = new ArrayList<>();
      for (Map.Entry<String, String> entry : statementsMap.entrySet()) {
        if (entry.getValue() != null) {
          statements.add(entry.getKey() + ": " + entry.getValue());
        }
        else {
          statements.add(entry.getKey());
        }
      }
      final PyExpression expression =
        elementGenerator.createExpressionFromText(LanguageLevel.forElement(myStatement), "{" + StringUtil.join(statements, ", ") + "}");
      assignedValue.replace(expression);
    }
  }
}
