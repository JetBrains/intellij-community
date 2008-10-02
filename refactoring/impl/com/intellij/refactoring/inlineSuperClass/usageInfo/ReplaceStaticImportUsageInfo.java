/*
 * User: anna
 * Date: 02-Oct-2008
 */
package com.intellij.refactoring.inlineSuperClass.usageInfo;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReplaceStaticImportUsageInfo extends FixableUsageInfo {
  private final PsiImportStaticStatement myStaticImportStatement;
  private final PsiClass myTargetClass;

  public ReplaceStaticImportUsageInfo(final PsiImportStaticStatement staticImportStatement, final PsiClass targetClass) {
    super(staticImportStatement);
    myStaticImportStatement = staticImportStatement;
    myTargetClass = targetClass;
  }

  public void fixUsage() throws IncorrectOperationException {
    final String memberName = myStaticImportStatement.getReferenceName();
    myStaticImportStatement.replace(JavaPsiFacade.getInstance(myStaticImportStatement.getProject()).getElementFactory().createImportStaticStatement(myTargetClass,
                                                                                                                                                    memberName != null ? memberName : "*"));
  }
}