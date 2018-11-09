// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.psi.PsiNameHelper;

public class IdentifierValidator implements InputValidator {
  private final Project myProject;

  public IdentifierValidator(final Project project) {
    myProject = project;
  }

  @Override
  public boolean checkInput(String inputString) {
    return PsiNameHelper.getInstance(myProject).isIdentifier(inputString);
  }

  @Override
  public boolean canClose(String inputString) {
    return checkInput(inputString);
  }
}