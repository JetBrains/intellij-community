package com.intellij.openapi.vcs.annotate;

import org.jetbrains.annotations.NotNull;

public interface AnnotationSourceSwitcher {
  @NotNull
  AnnotationSource getAnnotationSource(int lineNumber);
  boolean mergeSourceAvailable(int lineNumber);
  @NotNull
  LineAnnotationAspect getRevisionAspect();
  @NotNull
  AnnotationSource getDefaultSource();
  void switchTo(final AnnotationSource source);
}
