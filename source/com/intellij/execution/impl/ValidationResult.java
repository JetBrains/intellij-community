/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.impl;

/**
 * @author dyoma
 */
public final class ValidationResult {
  private final String myMessage;
  private final String myTitle;
  private final Runnable myQuickFix;

  public ValidationResult(final String message, final String title, final Runnable quickFix) {
    myMessage = message;
    myTitle = title;
    myQuickFix = quickFix;
  }

  public String getMessage() {
    return myMessage;
  }

  public String getTitle() {
    return myTitle;
  }

  public Runnable getQuickFix() {
    return myQuickFix;
  }
}
