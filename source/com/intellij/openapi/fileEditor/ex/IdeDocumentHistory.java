
package com.intellij.openapi.fileEditor.ex;

import com.intellij.openapi.project.Project;

public abstract class IdeDocumentHistory {
  public static IdeDocumentHistory getInstance(Project project) {
    return project.getComponent(IdeDocumentHistory.class);
  }

  public abstract void includeCurrentCommandAsNavigation();
  public abstract void includeCurrentPlaceAsChangePlace();
  public abstract void clearHistory();

  public abstract void back();
  public abstract void forward();

  public abstract boolean isBackAvailable();
  public abstract boolean isForwardAvailable();

  public abstract void navigatePreviousChange();
  public abstract boolean isNavigatePreviousChangeAvailable();
}