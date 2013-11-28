package com.intellij.designer;

import com.intellij.diagnostic.ITNReporter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;

/**
 * @author Alexander Lobas
 */
public class ErrorReporter extends ITNReporter {
  @Override
  public boolean showErrorInRelease(IdeaLoggingEvent event) {
    return true;
  }
}