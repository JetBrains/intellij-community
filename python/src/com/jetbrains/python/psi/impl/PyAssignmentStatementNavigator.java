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
package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
public class PyAssignmentStatementNavigator {
  private PyAssignmentStatementNavigator() {
  }

  @Nullable
  public static PyAssignmentStatement getStatementByTarget(final PsiElement element){
    final PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class);
    if (assignmentStatement != null){
      for (PyExpression expression : assignmentStatement.getTargets()) {
        if (element == expression){
          return assignmentStatement;
        }
        final PsiElement parent = element.getParent();
        if (parent == expression && parent.getFirstChild() == element && parent.getLastChild() == element){
          return assignmentStatement;
        }
      }
    }
    return null;
  }
}
