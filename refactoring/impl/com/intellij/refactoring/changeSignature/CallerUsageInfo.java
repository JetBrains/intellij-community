package com.intellij.refactoring.changeSignature;

import com.intellij.psi.PsiMethod;
import com.intellij.usageView.UsageInfo;

/**
 * @author ven
 */
public class CallerUsageInfo extends UsageInfo {
  private final boolean myToInsertParameter;
  private final boolean myToInsertException;

  public CallerUsageInfo(final PsiMethod method, boolean isToInsertParameter, boolean isToInsertException) {
    super(method);
    myToInsertParameter = isToInsertParameter;
    myToInsertException = isToInsertException;
  }

  public boolean isToInsertException() {
    return myToInsertException;
  }

  public boolean isToInsertParameter() {
    return myToInsertParameter;
  }

  public PsiMethod getMethod() {
    return (PsiMethod)getElement();
  }
}
