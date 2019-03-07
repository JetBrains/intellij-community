// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.io.File;

// TODO: Logic of this class can be extracted and abstracted from the concrete language.
public class PyPathCompletionContributor extends PyExtendedCompletionContributor {
  @Override
  protected void doFillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    if (isPath(parameters.getPosition())) {
      String text = normalize(parameters.getPosition().getText());
      addPathVariants(result, text);
    }
  }

  private boolean isPath(PsiElement position) {
    PyStringLiteralExpression sl = PsiTreeUtil.getContextOfType(position, PyStringLiteralExpression.class);
    return sl != null;
  }

  // handle paths in system independent way by using normal slashes for all systems
  static void addPathVariants(CompletionResultSet result, @NotNull String str) {
    // unquote string literal
    str = str.substring(1);
    int lastSep = str.lastIndexOf('/');
    if (lastSep < 0) return;

    // include '/' to properly handle windows root paths e.g. c:/
    File path = new File(lastSep == 0 ? "/" : str.substring(0, lastSep + 1));
    if (!path.exists()) return;

    File[] list = path.listFiles();
    if (list != null) {
      for (File file : list) {
        String item = file.getPath().replace(File.separatorChar, '/');
        item = item.startsWith("/") ? item.substring(1) : item;
        result.addElement(new PathLookupElement(item, file.isDirectory()));
      }
    }
  }

  private String normalize(String str) {
    return str.substring(0, str.lastIndexOf(CompletionInitializationContext.DUMMY_IDENTIFIER));
  }
}
