package com.intellij.psi.controlFlow;

import com.intellij.psi.PsiElement;

/**
 * @author mike
 */
public class AnalysisCanceledException extends Exception {
  private final PsiElement myErrorElement;

  public AnalysisCanceledException(PsiElement errorElement) {
    myErrorElement = errorElement;
  }

  public PsiElement getErrorElement() {
    return myErrorElement;
  }
}
