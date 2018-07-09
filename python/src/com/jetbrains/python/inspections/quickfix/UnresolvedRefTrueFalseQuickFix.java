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
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to replace true with True, false with False
 */
public class UnresolvedRefTrueFalseQuickFix implements LocalQuickFix {
  SmartPsiElementPointer<PsiElement> myElement;
  String newName;
  public UnresolvedRefTrueFalseQuickFix(PsiElement element) {
    myElement = SmartPointerManager.createPointer(element);
    char[] charArray = element.getText().toCharArray();
    charArray[0] = Character.toUpperCase(charArray[0]);
    newName = new String(charArray);
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.unresolved.reference.replace.$0", newName);
  }

  @NotNull
  public String getFamilyName() {
    return "Replace with True or False";
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

    PyExpression expression = elementGenerator.createExpressionFromText(newName);
    final PsiElement element = myElement.getElement();
    if (element != null) {
      element.replace(expression);
    }
  }
}
