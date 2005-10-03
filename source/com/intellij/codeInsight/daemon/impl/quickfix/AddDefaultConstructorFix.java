package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.psi.PsiClass;

public class AddDefaultConstructorFix extends AddMethodFix {
  public AddDefaultConstructorFix(PsiClass implClass) {
    super("public " + implClass.getName() + "() {}", implClass);
    setText(QuickFixBundle.message("add.default.constructor.text", implClass.getName()));
  }

  public String getFamilyName() {
    return QuickFixBundle.message("add.default.constructor.family");
  }
}
