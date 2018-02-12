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

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public abstract class PyAnnotator extends PyElementVisitor {
  private final boolean myTestMode = ApplicationManager.getApplication().isUnitTestMode();
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

  protected void addHighlightingAnnotation(@NotNull PsiElement target, @NotNull TextAttributesKey key) {
    addHighlightingAnnotation(target, key, HighlightSeverity.INFORMATION);
  }

  protected void addHighlightingAnnotation(@NotNull PsiElement target,
                                           @NotNull TextAttributesKey key,
                                           @NotNull HighlightSeverity severity) {
    final String message = myTestMode ? key.getExternalName() : null;
    // CodeInsightTestFixture#testHighlighting doesn't consider annotations with severity level < INFO
    final HighlightSeverity actualSeverity =
      myTestMode && severity.myVal < HighlightSeverity.INFORMATION.myVal ? HighlightSeverity.INFORMATION : severity;
    final Annotation annotation = getHolder().createAnnotation(actualSeverity, target.getTextRange(), message);
    annotation.setTextAttributes(key);
  }

  protected void addHighlightingAnnotation(@NotNull ASTNode target, @NotNull TextAttributesKey key) {
    addHighlightingAnnotation(target.getPsi(), key);
  }
}
