package com.jetbrains.python.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.ast.PyAstElementVisitor;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public class PyFrontendAnnotator extends PyAstElementVisitor implements PyAnnotatorBase {
  private final boolean myTestMode = ApplicationManager.getApplication().isUnitTestMode();
  private AnnotationHolder _holder;

  @Override
  public boolean isTestMode() {
    return myTestMode;
  }

  @Override
  public AnnotationHolder getHolder() {
    return _holder;
  }

  public void setHolder(AnnotationHolder holder) {
    _holder = holder;
  }

  @Override
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
