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
  @NotNull
  private final String myImportToAdd;
  /**
   * @param importToAdd string representing what to add (i.e. "from foo import bar")
   */
  public PyAddImportFix(@NotNull String importToAdd) {
    myImportToAdd = importToAdd;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return PyBundle.message("QFIX.add.import.add.import", myImportToAdd);
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final PsiElement element, @NotNull final ModPsiUpdater updater) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(project);
    PsiFile file = element.getContainingFile();
    final PyImportStatementBase statement =
      generator.createFromText(LanguageLevel.forElement(file), PyImportStatementBase.class, myImportToAdd);
    final PsiElement recommendedPosition = AddImportHelper.getFileInsertPosition(file);
    file.addAfter(statement, recommendedPosition);
  }
}

