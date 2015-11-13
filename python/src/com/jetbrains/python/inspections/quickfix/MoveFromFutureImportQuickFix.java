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
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexey.Ivanov
 */
public class MoveFromFutureImportQuickFix implements LocalQuickFix {
  @Override
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.move.from.future.import");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement problemElement = descriptor.getPsiElement();
    final PsiFile psiFile = problemElement.getContainingFile();
    if (psiFile instanceof PyFile) {
      AddImportHelper.addFromImportStatement(psiFile, (PyFromImportStatement)problemElement, AddImportHelper.ImportPriority.FUTURE, null);
      problemElement.delete();
    }
  }
}
