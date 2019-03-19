// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.codeInsight.completion.PyPathCompletionContributor;
import com.jetbrains.python.psi.PyStringElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PythonPathReferenceProvider extends PsiReferenceProvider {
  private static final Logger LOG = Logger.getInstance(PyPathCompletionContributor.class);

  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    String text = ((PyStringLiteralExpression)element).getStringValue();
    PyStringElement el = getLastStringElement((PyStringLiteralExpression)element);
    return new FileReferenceSet(text,
                                element,
                                0, //detectShift(element, el.getText()),
                                null,
                                SystemInfo.isFileSystemCaseSensitive).getAllReferences();
  }

  private PyStringElement getLastStringElement(PyStringLiteralExpression el) {
    return el.getStringElements().get(el.getStringElements().size() - 1);
  }


  public static int detectShift(PsiElement element, String text) {
    String elementText = element.getText();
    //int from = 0;
    //Pair<String, String> quotes = PyStringLiteralUtil.getQuotes(elementText);
    //if (quotes != null) {
    //  from = quotes.first.length();
    //}

    return elementText.indexOf(text, 0);
  }

  @Nullable
  static PyStringLiteralExpression getStringLiteral(@Nullable PsiElement o) {
    return PsiTreeUtil.getContextOfType(o, PyStringLiteralExpression.class);
  }
}
