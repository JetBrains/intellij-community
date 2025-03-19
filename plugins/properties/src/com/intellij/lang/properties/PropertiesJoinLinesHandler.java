// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  public int tryJoinLines(final @NotNull Document doc, final @NotNull PsiFile psiFile, int start, int end) {
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