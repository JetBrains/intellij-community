/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.refactoring.rename.RenameProcessor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyNamedParameter;
import org.jetbrains.annotations.NotNull;

/**
 * Parameter renaming.
* User: dcheryasov
*/
public class RenameParameterQuickFix implements LocalQuickFix {
  private final String myNewName;

  public RenameParameterQuickFix(String newName) {
    myNewName = newName;
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement elt = descriptor.getPsiElement();
    if (elt instanceof PyNamedParameter) {
      new RenameProcessor(project, elt, myNewName, false, true).run();
    }
  }

  @NotNull
  public String getFamilyName() {
    return "Rename parameter";
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.rename.parameter.to.$0", myNewName);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
