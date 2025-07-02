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
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use {@link Annotator} and 'annotator' extension point.
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated(forRemoval = true)
public abstract class PyAnnotator extends PyElementVisitor {
  public static final @NotNull ExtensionPointName<@NotNull PyAnnotator> EXTENSION_POINT_NAME =
    ExtensionPointName.create("Pythonid.pyAnnotator");

  private PyAnnotationHolder _holder;

  public AnnotationHolder getHolder() {
    PyAnnotationHolder holder = _holder;
    return holder != null ? holder.getOriginalHolder() : null;
  }

  public void setHolder(AnnotationHolder holder) {
    _holder = holder != null ? new PyAnnotationHolder(holder) : null;
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

  protected void addHighlightingAnnotation(@NotNull PsiElement target, @NotNull TextAttributesKey key) {
    _holder.addHighlightingAnnotation(target, key);
  }

  protected void addHighlightingAnnotation(@NotNull PsiElement target,
                                         @NotNull TextAttributesKey key,
                                         @NotNull HighlightSeverity severity) {
    _holder.addHighlightingAnnotation(target, key, severity);
  }

  protected void addHighlightingAnnotation(@NotNull ASTNode target, @NotNull TextAttributesKey key) {
    _holder.addHighlightingAnnotation(target, key);
  }
}
