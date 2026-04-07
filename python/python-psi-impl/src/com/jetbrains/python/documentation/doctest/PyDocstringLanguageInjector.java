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
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.psi.PyStringLiteralCoreUtil;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PyDocstringLanguageInjector implements LanguageInjector {
  private static final Pattern CODE_BLOCK_PATTERN =
    Pattern.compile("^\\s*\\.\\.\\s+(code-block|code|sourcecode)::\\s*(\\w+)?\\s*$");
  private static final Pattern OPTION_PATTERN = Pattern.compile("^\\s*:\\w[\\w-]*:");
  private static final Set<String> SPHINX_PYTHON_ALIASES = Set.of("py", "python", "py3", "python3");

  @Override
  public void getLanguagesToInject(final @NotNull PsiLanguageInjectionHost host,
                                   final @NotNull InjectedLanguagePlaces injectionPlacesRegistrar) {
    if (!(host instanceof PyStringLiteralExpression)) {
      return;
    }
    final Module module = ModuleUtilCore.findModuleForPsiElement(host);
    boolean analyzeDoctest = module == null
                             || PyDocumentationSettings.getInstance(module).isAnalyzeDoctest();
    if (!analyzeDoctest) return;

    if (DocStringUtil.getParentDefinitionDocString(host) == host) {
      final String text = host.getText();

      final Pair<String, String> quotes = PyStringLiteralCoreUtil.getQuotes(text);
      final String closingQuote = quotes == null ? text.substring(0, 1) : quotes.second;
      final List<String> strings = StringUtil.split(text, "\n", false);
      final int maxPosition = text.length();

      injectDoctestBlocks(strings, maxPosition, closingQuote, injectionPlacesRegistrar);
      injectCodeBlocks(strings, maxPosition, closingQuote, injectionPlacesRegistrar);
    }
  }

  private static void injectDoctestBlocks(@NotNull List<String> strings, int maxPosition, @NotNull String closingQuote,
                                          @NotNull InjectedLanguagePlaces injectionPlacesRegistrar) {
    boolean gotExample = false;
    int start = 0;
    int end = 0;
    int currentPosition = 0;
    boolean endsWithSlash = false;

    for (String string : strings) {
      final String trimmedString = string.trim();
      if (!trimmedString.startsWith(">>>") && !trimmedString.startsWith("...") && gotExample && start < end) {
        gotExample = false;
        if (!endsWithSlash) {
          injectionPlacesRegistrar.addPlace(PyDocstringLanguageDialect.getInstance(), TextRange.create(start, end), null, null);
        }
      }

      if (endsWithSlash && !trimmedString.endsWith("\\")) {
        endsWithSlash = false;
        injectionPlacesRegistrar.addPlace(PyDocstringLanguageDialect.getInstance(),
                                          TextRange.create(start, getEndOffset(currentPosition, string, maxPosition, closingQuote)), null,
                                          null);
      }

      if (trimmedString.startsWith(">>>")) {
        if (trimmedString.endsWith("\\")) {
          endsWithSlash = true;
        }

        if (!gotExample) {
          start = currentPosition;
        }

        gotExample = true;
        end = getEndOffset(currentPosition, string, maxPosition, closingQuote);
      }
      else if (trimmedString.startsWith("...") && gotExample) {
        if (trimmedString.endsWith("\\")) {
          endsWithSlash = true;
        }

        end = getEndOffset(currentPosition, string, maxPosition, closingQuote);
      }
      currentPosition += string.length();
    }
    if (gotExample && start < end) {
      injectionPlacesRegistrar.addPlace(PyDocstringLanguageDialect.getInstance(), TextRange.create(start, end), null, null);
    }
  }

  private static void injectCodeBlocks(@NotNull List<String> strings, int maxPosition, @NotNull String closingQuote,
                                       @NotNull InjectedLanguagePlaces injectionPlacesRegistrar) {
    int currentPosition = 0;

    for (int i = 0; i < strings.size(); i++) {
      String line = strings.get(i);
      Matcher matcher = CODE_BLOCK_PATTERN.matcher(line);

      if (matcher.matches()) {
        String language = matcher.group(2);
        if (language == null) {
          return;
        }
        String languageLowerCase = language.toLowerCase();

        if (SPHINX_PYTHON_ALIASES.contains(languageLowerCase)) {
          TextRange codeBlockRange = extractCodeBlockRange(strings, i, currentPosition, maxPosition, closingQuote);
          if (codeBlockRange != null) {
            injectionPlacesRegistrar.addPlace(PyDocstringLanguageDialect.getInstance(), codeBlockRange, null, null);
          }
        }
      }
      currentPosition += line.length();
    }
  }

  private static TextRange extractCodeBlockRange(@NotNull List<String> lines, int directiveLineIndex, int directivePosition,
                                                 int maxPosition, @NotNull String closingQuote) {
    String directiveLine = lines.get(directiveLineIndex);
    int directiveIndent = PyIndentUtil.getLineIndentSize(directiveLine);
    int currentPosition = directivePosition + directiveLine.length();

    int i = directiveLineIndex + 1;
    while (i < lines.size()) {
      String line = lines.get(i);
      if (OPTION_PATTERN.matcher(line).find() || line.trim().isEmpty()) {
        currentPosition += line.length();
        i++;
      }
      else {
        break;
      }
    }

    if (i >= lines.size()) {
      return null;
    }

    String firstContentLine = lines.get(i);
    int contentIndent = PyIndentUtil.getLineIndentSize(firstContentLine);

    if (contentIndent <= directiveIndent) {
      return null;
    }

    int contentStart = currentPosition;
    int contentEnd = currentPosition;

    while (i < lines.size()) {
      String line = lines.get(i);
      int lineIndent = PyIndentUtil.getLineIndentSize(line);

      if (!line.trim().isEmpty() && lineIndent < contentIndent) {
        break;
      }

      contentEnd = getEndOffset(currentPosition, line, maxPosition, closingQuote);
      currentPosition += line.length();
      i++;
    }

    return contentStart < contentEnd ? TextRange.create(contentStart, contentEnd) : null;
  }

  private static int getEndOffset(int start, String s, int maxPosition, String closingQuote) {
    int end;
    int length = s.length();
    if (s.trim().endsWith(closingQuote)) {
      length -= 3;
    }
    else if (start + length == maxPosition && (s.trim().endsWith("\"") || s.trim().endsWith("'"))) {
      length -= 1;
    }

    end = start + length;
    if (s.endsWith("\n")) {
      end -= 1;
    }
    return end;
  }
}
