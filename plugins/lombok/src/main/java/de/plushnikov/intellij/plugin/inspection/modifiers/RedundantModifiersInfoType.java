package de.plushnikov.intellij.plugin.inspection.modifiers;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiVariable;

public enum RedundantModifiersInfoType {

  CLASS(PsiClass.class),
  FIELD(PsiField.class),
  METHOD(PsiMethod.class),
  VARIABLE(PsiVariable.class),
  INNER_CLASS(PsiClass.class);

  private final Class<? extends PsiModifierListOwner> supportedClass;

  RedundantModifiersInfoType(Class<? extends PsiModifierListOwner> supportedClass) {
    this.supportedClass = supportedClass;
  }

  public Class<? extends PsiModifierListOwner> getSupportedClass() {
    return supportedClass;
  }
}
