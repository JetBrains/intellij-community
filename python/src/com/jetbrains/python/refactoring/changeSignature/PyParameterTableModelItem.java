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
package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.psi.PsiCodeFragment;
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase;

/**
* User : ktisha
*/
class PyParameterTableModelItem extends ParameterTableModelItemBase<PyParameterInfo> {

  public PyParameterTableModelItem(PyParameterInfo parameter,
                                   PsiCodeFragment typeCodeFragment,
                                   PsiCodeFragment defaultValueCodeFragment, boolean defaultInSignature) {
    super(parameter, typeCodeFragment, defaultValueCodeFragment);
  }

  @Override
  public boolean isEllipsisType() {
    return parameter.getName().startsWith("*");
  }

  public void setDefaultInSignature(boolean isDefault) {
    parameter.setDefaultInSignature(isDefault);
  }

  public boolean isDefaultInSignature() {
    return parameter.getDefaultInSignature();
  }

  public String getDefaultValue() {
    return defaultValueCodeFragment.getText();
  }
}