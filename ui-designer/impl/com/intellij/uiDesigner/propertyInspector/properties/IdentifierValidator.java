/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.06.2006
 * Time: 20:11:48
 */
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;

public class IdentifierValidator implements InputValidator {
  private final Project myProject;

  public IdentifierValidator(final Project project) {
    myProject = project;
  }

  public boolean checkInput(String inputString) {
    return PsiManager.getInstance(myProject).getNameHelper().isIdentifier(inputString);
  }

  public boolean canClose(String inputString) {
    return checkInput(inputString);
  }
}