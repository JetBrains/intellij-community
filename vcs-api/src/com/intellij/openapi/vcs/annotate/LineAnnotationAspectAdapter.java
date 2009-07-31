package com.intellij.openapi.vcs.annotate;

public abstract class LineAnnotationAspectAdapter implements LineAnnotationAspect {
  public String getTooltipText(int lineNumber) {
    return null;
  }
}
