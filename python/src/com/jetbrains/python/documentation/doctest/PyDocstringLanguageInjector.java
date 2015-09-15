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
package com.jetbrains.python.documentation.doctest;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PythonStringUtil;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: ktisha
 */
public class PyDocstringLanguageInjector implements LanguageInjector {
  @Override
  public void getLanguagesToInject(@NotNull final PsiLanguageInjectionHost host, @NotNull final InjectedLanguagePlaces injectionPlacesRegistrar) {
    if (!(host instanceof PyStringLiteralExpression)) {
      return;
    }
    final Module module = ModuleUtilCore.findModuleForPsiElement(host);
    if (module == null || !PyDocumentationSettings.getInstance(module).isAnalyzeDoctest()) return;

    final PyDocStringOwner
      docStringOwner = PsiTreeUtil.getParentOfType(host, PyDocStringOwner.class);
    if (docStringOwner != null && host.equals(docStringOwner.getDocStringExpression())) {
      int start = 0;
      int end = host.getTextLength() - 1;
      final String text = host.getText();

      final Pair<String,String> quotes = PythonStringUtil.getQuotes(text);
      final List<String> strings = StringUtil.split(text, "\n", false);

      boolean gotExample = false;

      int currentPosition = 0;
      int maxPosition = text.length();
      boolean endsWithSlash = false;
      for (String string : strings) {
        final String trimmedString = string.trim();
        if (!trimmedString.startsWith(">>>") && !trimmedString.startsWith("...") && gotExample && start < end) {
          gotExample = false;
          if (!endsWithSlash)
            injectionPlacesRegistrar.addPlace(PyDocstringLanguageDialect.getInstance(), TextRange.create(start, end),  null, null);
        }
        final String closingQuote = quotes == null ? text.substring(0, 1) : quotes.second;

        if (endsWithSlash && !trimmedString.endsWith("\\")) {
          endsWithSlash = false;
          injectionPlacesRegistrar.addPlace(PyDocstringLanguageDialect.getInstance(),
                                            TextRange.create(start, getEndOffset(currentPosition, string, maxPosition, closingQuote)),  null, null);
        }

        if (trimmedString.startsWith(">>>")) {
          if (trimmedString.endsWith("\\"))
            endsWithSlash = true;

          if (!gotExample)
            start = currentPosition;

          gotExample = true;
          end = getEndOffset(currentPosition, string, maxPosition, closingQuote);
        }
        else if (trimmedString.startsWith("...") && gotExample) {
          if (trimmedString.endsWith("\\"))
            endsWithSlash = true;

          end = getEndOffset(currentPosition, string, maxPosition, closingQuote);
        }
        currentPosition += string.length();
      }
      if (gotExample && start < end)
        injectionPlacesRegistrar.addPlace(PyDocstringLanguageDialect.getInstance(), TextRange.create(start, end),  null, null);
    }
  }

  private static int getEndOffset(int start, String s, int maxPosition, String closingQuote) {
    int end;
    int length = s.length();
    if (s.trim().endsWith(closingQuote))
      length -= 3;
    else if (start + length == maxPosition && (s.trim().endsWith("\"") || s.trim().endsWith("'")))
      length -= 1;

    end = start + length;
    if (s.endsWith("\n"))
      end -= 1;
    return end;
  }
}
