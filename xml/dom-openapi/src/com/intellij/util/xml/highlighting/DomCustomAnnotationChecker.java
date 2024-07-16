// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.highlighting;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Basic DOM inspection (see {@link BasicDomElementsInspection})
 * calls this annotator on all DOM elements with the given custom user-defined annotation.
 */
public abstract class DomCustomAnnotationChecker<T extends Annotation> {
  static final ExtensionPointName<DomCustomAnnotationChecker> EP_NAME = ExtensionPointName.create("com.intellij.dom.customAnnotationChecker");
  
  public abstract @NotNull Class<T> getAnnotationClass();

  public abstract List<DomElementProblemDescriptor> checkForProblems(@NotNull T t, @NotNull DomElement element, @NotNull DomElementAnnotationHolder holder, @NotNull DomHighlightingHelper helper);
}
