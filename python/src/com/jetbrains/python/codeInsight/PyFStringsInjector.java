/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.documentation.doctest.PyDocstringLanguageDialect;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class PyFStringsInjector extends PyInjectorBase {
  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    final PyStringLiteralExpression pyString = as(context, PyStringLiteralExpression.class);
    if (pyString == null) {
      return;
    }
    
    for (ASTNode node : pyString.getStringNodes()) {
      final String nodeText = node.getText();
      final int relNodeOffset = node.getTextRange().getStartOffset() - pyString.getTextRange().getStartOffset();
      final List<TextRange> ranges = getInjectionRanges(nodeText);
      for (TextRange range : ranges) {
        registrar.startInjecting(PyDocstringLanguageDialect.getInstance());
        registrar.addPlace(null, null, pyString, range.shiftRight(relNodeOffset));
        registrar.doneInjecting();
      }
    }
  }

  @VisibleForTesting
  @NotNull
  public static List<TextRange> getInjectionRanges(@NotNull String nodeText) {
    final List<TextRange> result = new ArrayList<>();
    final String nodePrefix = nodeText.substring(0, PyStringLiteralExpressionImpl.getPrefixLength(nodeText));
    final boolean isFormattedString = nodePrefix.toLowerCase(Locale.US).contains("f");
    if (isFormattedString) {
      int bracesBalance = 0;
      boolean insideChunk = false;
      String nestedLiteralQuotes = null;
      int chunkStart = 0;
      final TextRange nodeContentRange = PyStringLiteralExpressionImpl.getNodeTextRange(nodeText);
      int offset = nodeContentRange.getStartOffset();
      while (offset < nodeContentRange.getEndOffset()) {
        final char c1 = nodeText.charAt(offset);
        final char c2 = offset + 1 < nodeContentRange.getEndOffset() ? nodeText.charAt(offset + 1) : '\0';
        final char c3 = offset + 2 < nodeContentRange.getEndOffset() ? nodeText.charAt(offset + 2) : '\0';
        if (!insideChunk) {
          if ((c1 == '{' && c2 == '{') || (c1 == '}' && c2 == '}')) {
            offset += 2;
            continue;
          }
          else if (c1 == '{') {
            chunkStart = offset + 1;
            insideChunk = true;
          }
        }
        else if (nestedLiteralQuotes != null) {
          if (c1 == '\'' || c1 == '"') {
            final String expected;
            if (c2 == c1 && c3 == c1) {
              expected = StringUtil.repeatSymbol(c1, 3);
            }
            else {
              expected = String.valueOf(c1); 
            }
            if (nestedLiteralQuotes.equals(expected)) {
              nestedLiteralQuotes = null;
              offset += expected.length();
              continue;
            }
          }
          else if (c1 == '\\') {
            offset += 2;
            continue;
          }
        }
        else if (c1 == '\'' || c1 == '"') {
          if (c2 == c1 && c3 == c1) {
            nestedLiteralQuotes = StringUtil.repeatSymbol(c1, 3);
            offset += 3;
            continue;
          }
          nestedLiteralQuotes = String.valueOf(c1);
        }
        else if (c1 == '{' || c1 == '[' || c1 == '(') {
          bracesBalance++;
        }
        else if (bracesBalance > 0 && (c1 == '}' || c1 == ']' || c1 == ')')) {
          bracesBalance--;
        }
        else if (bracesBalance == 0 && (c1 == '}' || (c1 == '!' && c2 != '=') || c1 == ':')) {
          insideChunk = false;
          if (offset > chunkStart) {
            result.add(TextRange.create(chunkStart, offset));
          }
        }
        offset++;
      }
      if (insideChunk) {
        result.add(TextRange.create(chunkStart, nodeContentRange.getEndOffset()));
      }
    }
    return Collections.unmodifiableList(result);
  }

  @Nullable
  @Override
  public Language getInjectedLanguage(@NotNull PsiElement context) {
    return context instanceof PyStringLiteralExpression? PyDocstringLanguageDialect.getInstance() : null;
  }
}
