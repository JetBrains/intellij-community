/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.quickfix.DocstringQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class PyMissingOrEmptyDocstringInspection extends PyBaseDocstringInspection {
  @NotNull
  @Override
  public Visitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session) {
      @Override
      protected void checkDocString(@NotNull PyDocStringOwner node) {
        final PyStringLiteralExpression docStringExpression = node.getDocStringExpression();
        if (docStringExpression == null) {
          for (PyInspectionExtension extension : Extensions.getExtensions(PyInspectionExtension.EP_NAME)) {
            if (extension.ignoreMissingDocstring(node)) {
              return;
            }
          }
          PsiElement marker = null;
          if (node instanceof PyClass) {
            final ASTNode n = ((PyClass)node).getNameNode();
            if (n != null) marker = n.getPsi();
          }
          else if (node instanceof PyFunction) {
            final ASTNode n = ((PyFunction)node).getNameNode();
            if (n != null) marker = n.getPsi();
          }
          else if (node instanceof PyFile) {
            final TextRange tr = new TextRange(0, 0);
            final ProblemsHolder holder = getHolder();
            if (holder != null) {
              holder.registerProblem(node, tr, PyBundle.message("INSP.no.docstring"));
            }
            return;
          }
          if (marker == null) marker = node;
          if (node instanceof PyFunction || (node instanceof PyClass && ((PyClass)node).findInitOrNew(false, null) != null)) {
            registerProblem(marker, PyBundle.message("INSP.no.docstring"), new DocstringQuickFix(null, null));
          }
          else {
            registerProblem(marker, PyBundle.message("INSP.no.docstring"));
          }
        }
        else if (StringUtil.isEmptyOrSpaces(docStringExpression.getStringValue())) {
          registerProblem(docStringExpression, PyBundle.message("INSP.empty.docstring"));
        }
      }
    };
  }
}
