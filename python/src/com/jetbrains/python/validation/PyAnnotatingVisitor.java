/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * @author yole
 */
public class PyAnnotatingVisitor implements Annotator {
  private static final Logger LOGGER = Logger.getInstance(PyAnnotatingVisitor.class.getName());
  private static final Class[] ANNOTATOR_CLASSES = new Class[] {
    AssignTargetAnnotator.class,
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
      catch (InstantiationException e) {
        LOGGER.error(e);
        continue;
      }
      catch (IllegalAccessException e) {
        LOGGER.error(e);
        continue;
      }
      annotators.add(annotator);
    }
    myAnnotators = annotators.toArray(new PyAnnotator[annotators.size()]);
  }

  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    final PsiFile file = psiElement.getContainingFile();
    for (PyAnnotator annotator : myAnnotators) {
      if (file instanceof PyFileImpl && !((PyFileImpl)file).isAcceptedFor(annotator.getClass())) continue;
      annotator.annotateElement(psiElement, holder);
    }
  }
}
