/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.06.2006
 * Time: 20:11:48
 */
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiNameHelper;

public class IdentifierValidator implements InputValidator {
  private final Project myProject;

  public IdentifierValidator(final Project project) {
    myProject = project;
  }

  public boolean checkInput(String inputString) {
    return PsiNameHelper.getInstance(myProject).isIdentifier(inputString);
  }

  public boolean canClose(String inputString) {
    return checkInput(inputString);
  }
}