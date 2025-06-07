// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.psi.PsiCodeFragment;
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class PyParameterTableModelItem extends ParameterTableModelItemBase<PyParameterInfo> {

  PyParameterTableModelItem(PyParameterInfo parameter,
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