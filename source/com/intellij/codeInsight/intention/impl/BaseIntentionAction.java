package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiFile;

/**
 * @author Mike
 */
public abstract class BaseIntentionAction implements IntentionAction {
  private String myText = "";

  public String getText() {
    return myText;
  }

  protected void setText(String text) {
    myText = text;
  }

  public static boolean prepareTargetFile(final PsiFile file) {
    return CodeInsightUtil.prepareFileForWrite(file);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
