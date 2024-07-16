// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


public final class PyAnnotatingVisitor implements Annotator, DumbAware {
  private static final Logger LOGGER = Logger.getInstance(PyAnnotatingVisitor.class.getName());
  private static final Class[] ANNOTATOR_CLASSES = new Class[] {
    AssignTargetAnnotator.class,
    TypeAnnotationTargetAnnotator.class,
    ParameterListAnnotator.class,
    HighlightingAnnotator.class,
    ReturnAnnotator.class,
    TryExceptAnnotator.class,
    BreakContinueAnnotator.class,
    GlobalAnnotator.class,
    ImportAnnotator.class,
    PyBuiltinAnnotator.class,
    UnsupportedFeatures.class,
    PyAsyncAwaitAnnotator.class
  };

  private final PyAnnotator[] myAnnotators;

  public PyAnnotatingVisitor() {
    final List<PyAnnotator> annotators = new ArrayList<>();
    for (Class cls : ANNOTATOR_CLASSES) {
      PyAnnotator annotator;
      try {
        annotator = (PyAnnotator)cls.newInstance();
      }
      catch (InstantiationException | IllegalAccessException e) {
        LOGGER.error(e);
        continue;
      }
      annotators.add(annotator);
    }
    myAnnotators = annotators.toArray(new PyAnnotator[0]);
  }

  @Override
  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    PyAnnotatorBase.runAnnotators(psiElement, holder, myAnnotators);
  }
}
