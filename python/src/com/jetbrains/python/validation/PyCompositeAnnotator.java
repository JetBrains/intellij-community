// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyCompositeAnnotator implements Annotator {
  private final PyAnnotator[] myAnnotators = ExtensionPointName.<PyAnnotator>create("Pythonid.pyAnnotator").getExtensions();

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    PyAnnotatingVisitor.runAnnotators(element, holder, myAnnotators);
  }
}
