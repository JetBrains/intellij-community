// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.jetbrains.python.documentation.doctest.PyDocstringLanguageDialect;
import com.intellij.python.reStructuredText.psi.RestLine;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class PyRestDocstringLanguageInjector implements LanguageInjector {
  @Override
  public void getLanguagesToInject(@NotNull final PsiLanguageInjectionHost host, @NotNull final InjectedLanguagePlaces injectionPlacesRegistrar) {
    if (host instanceof RestLine) {
      int start = 0;
      int end = host.getTextLength() - 1;
      final String text = host.getText();
      final List<String> strings = StringUtil.split(text, "\n", false);

      boolean gotExample = false;

      int currentPosition = 0;
      boolean endsWithSlash = false;
      for (String string : strings) {
        final String trimmedString = string.trim();
        if (!trimmedString.startsWith(">>>") && !trimmedString.startsWith("...") && gotExample && start < end) {
          gotExample = false;
          if (!endsWithSlash)
            injectionPlacesRegistrar.addPlace(PyDocstringLanguageDialect.getInstance(), TextRange.create(start, end),  null, null);
        }
        if (endsWithSlash) {
          endsWithSlash = false;
          injectionPlacesRegistrar.addPlace(PyDocstringLanguageDialect.getInstance(),
                                            TextRange.create(start, getEndOffset(currentPosition, string)),  null, null);
        }

        if (trimmedString.startsWith(">>>")) {
          if (trimmedString.endsWith("\\"))
            endsWithSlash = true;

          if (!gotExample)
            start = currentPosition;

          gotExample = true;
          end = getEndOffset(currentPosition, string);
        }
        else if (trimmedString.startsWith("...") && gotExample) {
          if (trimmedString.endsWith("\\"))
            endsWithSlash = true;

          end = getEndOffset(currentPosition, string);
        }
        currentPosition += string.length();
      }
      if (gotExample && start < end)
        injectionPlacesRegistrar.addPlace(PyDocstringLanguageDialect.getInstance(), TextRange.create(start, end),  null, null);
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
