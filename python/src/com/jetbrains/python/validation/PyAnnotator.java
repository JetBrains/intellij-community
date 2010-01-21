package com.jetbrains.python.validation;

import com.jetbrains.python.psi.PyElementVisitor;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.PsiElement;

/**
 * @author yole
 */
public abstract class PyAnnotator extends PyElementVisitor {
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

  protected void markError(PsiElement element, String message) {
    getHolder().createErrorAnnotation(element, message);
  }
}
