// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.restructuredtext.python;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.restructuredtext.psi.RestLine;
import com.jetbrains.python.documentation.doctest.PyDoctestLanguageDialect;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class PyRestDocstringLanguageInjector implements LanguageInjector {
  @Override
  public void getLanguagesToInject(final @NotNull PsiLanguageInjectionHost host, final @NotNull InjectedLanguagePlaces injectionPlacesRegistrar) {
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
        final boolean isPromptLine = trimmedString.startsWith(">>>") || trimmedString.startsWith("...") && gotExample;
        if (gotExample && !isPromptLine) {
          if (endsWithSlash) {
            // A bare line continuing a backslash-terminated statement still belongs to the example
            endsWithSlash = trimmedString.endsWith("\\");
            end = getEndOffset(currentPosition, string);
            currentPosition += string.length();
            continue;
          }
          gotExample = false;
          if (start < end) {
            injectionPlacesRegistrar.addPlace(PyDoctestLanguageDialect.getInstance(), TextRange.create(start, end), null, null);
          }
        }

        if (trimmedString.startsWith(">>>")) {
          if (!gotExample)
            start = currentPosition;

          gotExample = true;
          endsWithSlash = trimmedString.endsWith("\\");
          end = getEndOffset(currentPosition, string);
        }
        else if (trimmedString.startsWith("...") && gotExample) {
          endsWithSlash = trimmedString.endsWith("\\");
          end = getEndOffset(currentPosition, string);
        }
        currentPosition += string.length();
      }
      if (gotExample && start < end)
        injectionPlacesRegistrar.addPlace(PyDoctestLanguageDialect.getInstance(), TextRange.create(start, end), null, null);
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
