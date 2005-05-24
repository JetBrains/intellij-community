package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.IntentionAction;

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

  public boolean startInWriteAction() {
    return true;
  }
}
