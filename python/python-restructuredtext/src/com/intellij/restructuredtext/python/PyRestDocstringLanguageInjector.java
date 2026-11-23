// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.restructuredtext.python;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.restructuredtext.psi.RestLine;
import com.jetbrains.python.documentation.doctest.PyDoctestExamples;
import com.jetbrains.python.documentation.doctest.PyDoctestLanguageDialect;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class PyRestDocstringLanguageInjector implements LanguageInjector {
  @Override
  public void getLanguagesToInject(final @NotNull PsiLanguageInjectionHost host, final @NotNull InjectedLanguagePlaces injectionPlacesRegistrar) {
    if (host instanceof RestLine) {
      final List<String> strings = StringUtil.split(host.getText(), "\n", false);
      for (TextRange range : PyDoctestExamples.findRanges(strings, PyRestDocstringLanguageInjector::getEndOffset)) {
        injectionPlacesRegistrar.addPlace(PyDoctestLanguageDialect.getInstance(), range, null, null);
      }
    }
  }

  private static int getEndOffset(int start, String s) {
    int length = s.length();
    int end = start + length;
    if (s.endsWith("\n"))
      end -= 1;
    return end;
  }
}
