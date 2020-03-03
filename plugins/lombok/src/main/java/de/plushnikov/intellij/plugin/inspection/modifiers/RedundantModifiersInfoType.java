package de.plushnikov.intellij.plugin.inspection.modifiers;

import com.intellij.psi.*;

public enum RedundantModifiersInfoType {

  CLASS(PsiClass.class),
  FIELD(PsiField.class),
  METHOD(PsiMethod.class),
  INNER_CLASS(PsiClass.class);

  private final Class<? extends PsiModifierListOwner> supportedClass;

  RedundantModifiersInfoType(Class<? extends PsiModifierListOwner> supportedClass) {
    this.supportedClass = supportedClass;
  }

  public Class<? extends PsiModifierListOwner> getSupportedClass() {
    return supportedClass;
  }
}
