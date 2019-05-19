// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.unresolved.reference.replace.$0", newName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return "Replace with True or False";
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

    PyExpression expression = elementGenerator.createExpressionFromText(newName);
    final PsiElement element = myElement.getElement();
    if (element != null) {
      element.replace(expression);
    }
  }
}
