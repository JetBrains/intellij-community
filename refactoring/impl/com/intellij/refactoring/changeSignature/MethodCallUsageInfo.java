package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.usageView.UsageInfo;

/**
 * @author ven
 */
public class MethodCallUsageInfo extends UsageInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.changeSignature.MethodCallUsageInfo");
  private final boolean myToChangeArguments;
  private final boolean myToCatchExceptions;
  private final PsiMethod myReferencedMethod;

  public boolean isToCatchExceptions() {
    return myToCatchExceptions;
  }

  public boolean isToChangeArguments() {
    return myToChangeArguments;
  }

  public MethodCallUsageInfo(final PsiElement ref, boolean isToChangeArguments, boolean isToCatchExceptions) {
    super(ref);
    myToChangeArguments = isToChangeArguments;
    myToCatchExceptions = isToCatchExceptions;
    myReferencedMethod = resolveMethod(ref);
  }

  private static PsiMethod resolveMethod(final PsiElement ref) {
    if (ref instanceof PsiEnumConstant) return ((PsiEnumConstant)ref).resolveConstructor();
    PsiElement parent = ref.getParent();
    if (parent instanceof PsiCall) {
      return ((PsiCall)parent).resolveMethod();
    }
    else if (parent instanceof PsiAnonymousClass) {
      return ((PsiNewExpression)parent.getParent()).resolveConstructor();
    }
    LOG.assertTrue(false, "Unknown reference");

    return null;
  }

  public PsiMethod getReferencedMethod() {
    return myReferencedMethod;
  }
}
