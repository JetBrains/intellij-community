/*
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.refactoring.inlineSuperClass.usageInfo;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiImportStatement;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReplaceImportUsageInfo extends FixableUsageInfo{
  private final PsiImportStatement myImportStatement;
  private final PsiClass myTargetClass;

  public ReplaceImportUsageInfo(PsiImportStatement importStatement, PsiClass targetClass) {
    super(importStatement);
    myImportStatement = importStatement;
    myTargetClass = targetClass;
  }

  public void fixUsage() throws IncorrectOperationException {
    final PsiImportStatement importStatement =
      JavaPsiFacade.getInstance(myTargetClass.getProject()).getElementFactory().createImportStatement(myTargetClass);
    myImportStatement.replace(importStatement);
  }
}