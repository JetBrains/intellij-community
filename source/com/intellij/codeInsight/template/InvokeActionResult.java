
package com.intellij.codeInsight.template;

import com.intellij.psi.PsiElement;

public class InvokeActionResult implements Result{
  private final Runnable myAction;

  public InvokeActionResult(Runnable action) {
    myAction = action;
  }

  public Runnable getAction() {
    return myAction;
  }

  public boolean equalsToText(String text, PsiElement context) {
    return true; //no text result will be provided anyway
  }

  public String toString() {
    return "";
  }
}