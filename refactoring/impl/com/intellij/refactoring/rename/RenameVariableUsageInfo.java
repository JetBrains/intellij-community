package com.intellij.refactoring.rename;

import com.intellij.usageView.UsageInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiVariable;

/**
 * @author dsl
 */
public class RenameVariableUsageInfo extends UsageInfo {
  private final String myNewName;

  public RenameVariableUsageInfo(PsiVariable element, String newName) {
    super(element);
    myNewName = newName;
  }

  public String getNewName() {
    return myNewName;
  }
}
