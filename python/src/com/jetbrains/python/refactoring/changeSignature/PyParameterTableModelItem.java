package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.psi.PsiCodeFragment;
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase;

/**
* User : ktisha
*/
class PyParameterTableModelItem extends ParameterTableModelItemBase<PyParameterInfo> {

  private boolean myDefaultInSignature;

  public PyParameterTableModelItem(PyParameterInfo parameter,
                                   PsiCodeFragment typeCodeFragment,
                                   PsiCodeFragment defaultValueCodeFragment, boolean defaultInSignature) {
    super(parameter, typeCodeFragment, defaultValueCodeFragment);
    myDefaultInSignature = defaultInSignature;
  }

  @Override
  public boolean isEllipsisType() {
    return parameter.getName().startsWith("*");
  }

  public void setDefaultInSignature(boolean isDefault) {
    myDefaultInSignature = isDefault;
  }

  public boolean isDefaultInSignature() {
    return myDefaultInSignature;
  }
}