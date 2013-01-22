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