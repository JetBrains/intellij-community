// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.impl.PyFileImpl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


public class PyAnnotatingVisitor implements Annotator {
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
    UnsupportedFeatures.class
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
    runAnnotators(psiElement, holder, myAnnotators);
  }

  static void runAnnotators(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder, PyAnnotator[] annotators) {
    PsiFile file = holder.getCurrentAnnotationSession().getFile();
    for (PyAnnotator annotator : annotators) {
      if (file instanceof PyFileImpl && !((PyFileImpl)file).isAcceptedFor(annotator.getClass())) continue;
      annotator.annotateElement(psiElement, holder);
    }
  }
}
