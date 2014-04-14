/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyPassStatement;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import org.jetbrains.annotations.NotNull;

public class PyRemoveStatementQuickFix implements LocalQuickFix {
  @NotNull
  @Override
  public String getName() {
    return PyBundle.message("QFIX.NAME.remove.statement");
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PyStatement statement = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PyStatement.class, false);
    if (statement != null) {
      final PyStatementList statementList = PsiTreeUtil.getParentOfType(statement, PyStatementList.class);
      if (statementList != null) {
        if (statementList.getStatements().length == 1) {
          final PyPassStatement passStatement = PyElementGenerator.getInstance(project).createPassStatement();
          statementList.addBefore(passStatement, statement);
        }
      }
      statement.delete();
    }
  }
}
