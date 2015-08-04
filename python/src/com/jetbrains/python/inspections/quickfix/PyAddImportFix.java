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
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportStatementBase;
import org.jetbrains.annotations.NotNull;

/**
 * Quick fix that adds import to file
 *
 * @author Ilya.Kazakevich
 */
public class PyAddImportFix implements LocalQuickFix {
  @NotNull
  private final String myImportToAdd;
  @NotNull
  private final PyFile myFile;

  /**
   * @param importToAdd string representing what to add (i.e. "from foo import bar")
   * @param file where to add
   */
  public PyAddImportFix(@NotNull final String importToAdd, @NotNull final PyFile file) {
    myImportToAdd = importToAdd;
    myFile = file;
  }

  @NotNull
  @Override
  public String getName() {
    return PyBundle.message("QFIX.add.import.add.import", myImportToAdd);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(project);
    final PyImportStatementBase statement =
      generator.createFromText(LanguageLevel.forElement(myFile), PyImportStatementBase.class, myImportToAdd);
    final PsiElement recommendedPosition = AddImportHelper.getFileInsertPosition(myFile);
    myFile.addAfter(statement, recommendedPosition);
  }
}

