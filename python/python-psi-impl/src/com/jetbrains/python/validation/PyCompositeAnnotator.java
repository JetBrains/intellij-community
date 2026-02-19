// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public final class PyCompositeAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    for (PyAnnotator annotator : PyAnnotator.EXTENSION_POINT_NAME.getExtensionList()) {
      annotator.annotateElement(element, holder);
    }
  }
}
