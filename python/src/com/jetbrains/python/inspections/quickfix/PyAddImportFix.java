// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyImportStatementBase;
import org.jetbrains.annotations.NotNull;

/**
 * Quick fix that adds import to file
 *
 * @author Ilya.Kazakevich
 */
public class PyAddImportFix extends PsiUpdateModCommandQuickFix {
  private final @NotNull String myImportToAdd;
  /**
   * @param importToAdd string representing what to add (i.e. "from foo import bar")
   */
  public PyAddImportFix(@NotNull String importToAdd) {
    myImportToAdd = importToAdd;
  }

  @Override
  public @NotNull String getFamilyName() {
    return PyBundle.message("QFIX.add.import.add.import", myImportToAdd);
  }

  @Override
  public void applyFix(final @NotNull Project project, final @NotNull PsiElement element, final @NotNull ModPsiUpdater updater) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(project);
    PsiFile file = element.getContainingFile();
    final PyImportStatementBase statement =
      generator.createFromText(LanguageLevel.forElement(file), PyImportStatementBase.class, myImportToAdd);
    final PsiElement recommendedPosition = AddImportHelper.getFileInsertPosition(file);
    file.addAfter(statement, recommendedPosition);
  }
}

