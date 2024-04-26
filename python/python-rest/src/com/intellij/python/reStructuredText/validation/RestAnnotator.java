// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.PsiElement;

/**
 * User : catherine
 */
public abstract class RestAnnotator extends RestElementVisitor {
  private AnnotationHolder _holder;

  public AnnotationHolder getHolder() {
    return _holder;
  }

  public void setHolder(AnnotationHolder holder) {
    _holder = holder;
  }

  public synchronized void annotateElement(final PsiElement psiElement, final AnnotationHolder holder) {
    setHolder(holder);
    try {
      psiElement.accept(this);
    }
    finally {
      setHolder(null);
    }
  }

}
