/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.properties;

import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PropertiesJoinLinesHandler implements JoinLinesHandlerDelegate {
  @Override
  public int tryJoinLines(@NotNull final Document doc, @NotNull final PsiFile psiFile, int start, int end) {
    if (!(psiFile instanceof PropertiesFile)) return -1;

    final String documentText = doc.getText();
    final String documentTextTillEndOfFirstLine = documentText.substring(0, start + 1);
    if (!PropertiesUtil.isUnescapedBackSlashAtTheEnd(documentTextTillEndOfFirstLine)) return CANNOT_JOIN;

    if (end < documentText.length() && startsWithEscapedWhitespace(documentText.substring(end))) {
      // if the second line starts with escaped whitespace (e.g. '\ '), then remove it too
      end ++;
    }

    // strip the continuation char '\',
    // the leading whitespaces on the second line and
    // the optional '\' if the text on the second line starts with '\ '
    doc.deleteString(start, end);
    return start;
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean startsWithEscapedWhitespace(@Nullable String text) {
    if (text == null) return false;
    if (text.length() < 2) return false;

    final char backslash = text.charAt(0);
    final char whitespace = text.charAt(1);

    return backslash == '\\' && (whitespace == ' ' || whitespace == '\t');
  }
}