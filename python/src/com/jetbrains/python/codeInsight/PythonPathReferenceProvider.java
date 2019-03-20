// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

public class PythonPathReferenceProvider extends PsiReferenceProvider {
  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    String text = ((PyStringLiteralExpression)element).getStringValue();
    return new FileReferenceSet(text,
                                element,
                                0, //detectShift(element, el.getText()),
                                null,
                                SystemInfo.isFileSystemCaseSensitive,
                                true,
                                null,
                                true,
                                true,
                                true).getAllReferences();
  }
}
