package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.psi.PsiClass;

public class AddDefaultConstructorFix extends AddMethodFix {
  public AddDefaultConstructorFix(PsiClass implClass) {
    super("public " + implClass.getName() + "() {}", implClass);
    setText("Add Public No-args Constructor to "+implClass.getName());
  }

  public String getFamilyName() {
    return "Add Default Constructor";
  }
}
