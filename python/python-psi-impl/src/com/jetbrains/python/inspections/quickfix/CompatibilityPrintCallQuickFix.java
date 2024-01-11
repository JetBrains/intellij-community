// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.modcommand.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: catherine
 *
 * QuickFix to replace statement that has no effect with function call
 */
public class CompatibilityPrintCallQuickFix extends ModCommandBatchQuickFix {
  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.statement.effect");
  }

  @Override
  public @NotNull ModCommand perform(@NotNull Project project, @NotNull List<ProblemDescriptor> descriptors) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    return ModCommand.psiUpdate(ActionContext.from(descriptors.get(0)), updater -> {
      List<PsiElement> elements = ContainerUtil.map(descriptors, d -> updater.getWritable(d.getStartElement()));
      PsiFile file = elements.get(0).getContainingFile();
      for (PsiElement element : elements) {
        if (element.isValid()) {
          replace(element, elementGenerator);
        }
      }
      AddImportHelper.addOrUpdateFromImportStatement(file,
                                                     "__future__",
                                                     "print_function",
                                                     null,
                                                     AddImportHelper.ImportPriority.FUTURE,
                                                     null);
    });
  }

  private static void replace(PsiElement expression, PyElementGenerator elementGenerator) {
    final StringBuilder stringBuilder = new StringBuilder("print(");
    final PyExpression[] target = PsiTreeUtil.getChildrenOfType(expression, PyExpression.class);
    if (target != null) {
      stringBuilder.append(StringUtil.join(target, o -> o.getText(), ", "));
    }
    stringBuilder.append(")");
    expression.replace(elementGenerator.createFromText(LanguageLevel.forElement(expression), PyElement.class,
                                                       stringBuilder.toString()));
  }
}
