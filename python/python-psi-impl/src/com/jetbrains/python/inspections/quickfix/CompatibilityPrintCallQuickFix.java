// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandBatchQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * User: catherine
 * <p>
 * QuickFix to replace statement that has no effect with function call
 */
public class CompatibilityPrintCallQuickFix extends ModCommandBatchQuickFix {
  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("QFIX.statement.effect");
  }

  @Override
  public @NotNull ModCommand perform(@NotNull Project project, @NotNull List<ProblemDescriptor> descriptors) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    return ModCommand.psiUpdate(ActionContext.from(descriptors.get(0)), updater -> {
      List<PsiElement> writableElements = ContainerUtil.map(descriptors, d -> updater.getWritable(d.getStartElement()));
      MultiMap<PsiFile, PsiElement> groupedElements =
        ContainerUtil.groupBy(writableElements, element -> element.getContainingFile());
      for (Map.Entry<PsiFile, Collection<PsiElement>> entry : groupedElements.entrySet()) {
        PsiFile file = entry.getKey();
        for (PsiElement element : entry.getValue()) {
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
      }
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
