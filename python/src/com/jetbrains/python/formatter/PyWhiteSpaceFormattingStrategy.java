// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.formatter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.StaticSymbolWhiteSpaceDefinitionStrategy;
import com.jetbrains.python.editor.PythonEnterHandler;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import org.jetbrains.annotations.NotNull;

public class PyWhiteSpaceFormattingStrategy extends StaticSymbolWhiteSpaceDefinitionStrategy {
  public PyWhiteSpaceFormattingStrategy() {
    super('\\');
  }

  @Override
  public CharSequence adjustWhiteSpaceIfNecessary(@NotNull CharSequence whiteSpaceText,
                                                  @NotNull PsiElement startElement,
                                                  int startOffset,
                                                  int endOffset,
                                                  CodeStyleSettings codeStyleSettings) {
    CharSequence whiteSpace =  super.adjustWhiteSpaceIfNecessary(whiteSpaceText, startElement, startOffset, endOffset, codeStyleSettings);
    if (whiteSpace.length() > 0 && whiteSpace.charAt(0) == '\n' && !Strings.contains(whiteSpace, 0, whiteSpace.length(), '\\') &&
        PythonEnterHandler.needInsertBackslash(startElement.getContainingFile(), startOffset, false)) {
      return addBackslashPrefix(whiteSpace, codeStyleSettings);
    }
    return whiteSpace;
  }

  private static String addBackslashPrefix(CharSequence whiteSpace, CodeStyleSettings settings) {
    PyCodeStyleSettings pySettings = settings.getCustomSettings(PyCodeStyleSettings.class);
    return (pySettings.SPACE_BEFORE_BACKSLASH ? " \\" : "\\") + whiteSpace.toString();
  }

  /**
   * Python uses backslashes at the end of the line as indication that next line is an extension of the current one.
   * <p/>
   * Hence, we need to preserve them during white space manipulation.
   *
   *
   * @param whiteSpaceText    white space text to use by default for replacing sub-sequence of the given text
   * @param text              target text which region is to be replaced by the given white space symbols
   * @param startOffset       start offset to use with the given text (inclusive)
   * @param endOffset         end offset to use with the given text (exclusive)
   * @param codeStyleSettings the code style settings
   * @param nodeAfter
   * @return                  symbols to use for replacing {@code [startOffset; endOffset)} sub-sequence of the given text
   */
  @NotNull
  @Override
  public CharSequence adjustWhiteSpaceIfNecessary(@NotNull CharSequence whiteSpaceText,
                                                  @NotNull CharSequence text,
                                                  int startOffset,
                                                  int endOffset,
                                                  CodeStyleSettings codeStyleSettings, ASTNode nodeAfter) {
    // the general idea is that '\' symbol before line feed should be preserved
    Int2IntMap initialBackSlashes = countBackSlashes(text, startOffset, endOffset);
    if (initialBackSlashes.isEmpty()) {
      if (nodeAfter != null && whiteSpaceText.length() > 0 && whiteSpaceText.charAt(0) == '\n' &&
        PythonEnterHandler.needInsertBackslash(nodeAfter, false)) {
        return addBackslashPrefix(whiteSpaceText, codeStyleSettings);
      }
      return whiteSpaceText;
    }

    Int2IntMap newBackSlashes = countBackSlashes(whiteSpaceText, 0, whiteSpaceText.length());
    boolean continueProcessing = false;
    IntIterator iterator = initialBackSlashes.keySet().iterator();
    while (iterator.hasNext()) {
      if (!newBackSlashes.containsKey(iterator.nextInt())) {
        continueProcessing = true;
        break;
      }
    }
    if (!continueProcessing) {
      return whiteSpaceText;
    }

    PyCodeStyleSettings settings = codeStyleSettings.getCustomSettings(PyCodeStyleSettings.class);
    StringBuilder result = new StringBuilder();
    int line = 0;
    for (int i = 0; i < whiteSpaceText.length(); i++) {
      char c = whiteSpaceText.charAt(i);
      if (c != '\n') {
        result.append(c);
        continue;
      }
      if (!newBackSlashes.containsKey(line++)) {
        if ((i == 0 || whiteSpaceText.charAt(i - 1) != ' ') && settings.SPACE_BEFORE_BACKSLASH) {
          result.append(' ');
        }
        result.append('\\');
      }
      result.append(c);
    }
    return result;
  }

  /**
   * Counts number of back slashes per-line.
   *
   * @param text      target text
   * @param start     start offset to use with the given text (inclusive)
   * @param end       end offset to use with the given text (exclusive)
   * @return          map that holds '{@code line number -> number of back slashes}' mapping for the target text
   */
  static @NotNull Int2IntMap countBackSlashes(CharSequence text, int start, int end) {
    Int2IntMap result=new Int2IntOpenHashMap();
    int line = 0;
    if (end > text.length()) {
      end = text.length();
    }
    for (int i = start; i < end; i++) {
      char c = text.charAt(i);
      switch (c) {
        case '\n':
          line++;
          break;
        case '\\':
          result.put(line, 1);
          break;
      }
    }
    return result;
  }
}
