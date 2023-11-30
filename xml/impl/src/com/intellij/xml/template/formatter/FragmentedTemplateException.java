// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.template.formatter;

import com.intellij.psi.PsiErrorElement;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown if there are syntax errors in the template which make it difficult or impossible to merge a mix of template and markup/data
 * languages.
 */
public class FragmentedTemplateException extends Exception {
  private PsiErrorElement myErrorElement;

  public FragmentedTemplateException() {
  }

  public FragmentedTemplateException(@Nullable PsiErrorElement errorElement) {
    myErrorElement = errorElement;
  }

  public @Nullable PsiErrorElement getErrorElement() {
    return myErrorElement;
  }
}
