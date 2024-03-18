// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.python.reStructuredText.RestFileType;
import com.intellij.python.reStructuredText.RestLanguage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User : catherine
 */
public class RestAnnotatingVisitor implements Annotator {
  private static final Logger LOGGER = Logger.getInstance(RestAnnotatingVisitor.class.getName());
  private final List<RestAnnotator> myAnnotators = new ArrayList<>();

  public RestAnnotatingVisitor() {
    for (Class<? extends RestAnnotator> cls : ((RestLanguage)RestFileType.INSTANCE.getLanguage()).getAnnotators()) {
      RestAnnotator annotator;
      try {
        annotator = cls.newInstance();
      }
      catch (InstantiationException | IllegalAccessException e) {
        LOGGER.error(e);
        continue;
      }
      myAnnotators.add(annotator);
    }
  }

  @Override
  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    for(RestAnnotator annotator: myAnnotators) {
      annotator.annotateElement(psiElement, holder);
    }
  }
}
