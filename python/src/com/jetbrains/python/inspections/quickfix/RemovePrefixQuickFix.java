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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 */
public class RemovePrefixQuickFix implements LocalQuickFix {
  private final String myPrefix;

  public RemovePrefixQuickFix(String prefix) {
    myPrefix = prefix;
  }

  @NotNull
  @Override
  public String getName() {
    return PyBundle.message("INTN.remove.leading.$0", myPrefix);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return PyBundle.message("INTN.remove.leading.prefix");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PyStringLiteralExpression pyString = as(descriptor.getPsiElement(), PyStringLiteralExpression.class);
    if (pyString != null) {
      final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      for (ASTNode node : pyString.getStringNodes()) {
        final String nodeText = node.getText();
        final int prefixLength = PyStringLiteralExpressionImpl.getPrefixLength(nodeText);
        if (nodeText.substring(0, prefixLength).equalsIgnoreCase(myPrefix)) {
          final PyStringLiteralExpression replacement = elementGenerator.createStringLiteralAlreadyEscaped(nodeText.substring(prefixLength));
          node.getPsi().replace(replacement.getFirstChild());
        }
      }
    }
  }
}