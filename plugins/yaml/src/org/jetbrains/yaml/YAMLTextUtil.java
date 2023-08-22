// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * This is text-level utility collection
 * <p/>
 * Many of this methods could be used in other plug-ins (especially for indent-based languages).
 * Maybe it will be better to move this class or part of it into IDEA platform.
 */
public final class YAMLTextUtil {
  private YAMLTextUtil() {}


  // Copy-paste from com.jetbrains.python.editor.PythonCopyPasteProcessor.getLineStartSafeOffset
  /** @return start line character number or document border if specified line is outside the document range */
  public static int getLineStartSafeOffset(@NotNull Document document, int line) {
    if (line >= document.getLineCount()) return document.getTextLength();
    if (line < 0) return 0;
    return document.getLineStartOffset(line);
  }

  // Copy-paste from com.jetbrains.python.psi.PyIndentUtil#getLineIndentSize(CharSequence)
  /** @return text first line indent size (just character number) */
  public static int getStartIndentSize(@NotNull CharSequence text) {
    int stop;
    for (stop = 0; stop < text.length(); stop++) {
      final char c = text.charAt(stop);
      if (!(c == ' ' || c == '\t')) {
        break;
      }
    }
    return stop;
  }

  // This method is similar with com.jetbrains.python.psi.PyIndentUtil.changeIndent
  /**
   * This method indents each line of text by specified space number.
   * Empty lines will be indented also.
   *
   * @return indented text
   */
  @NotNull
  public static String indentText(@NotNull String text, int indent) {
    StringBuilder buffer = new StringBuilder();
    String indentString = StringUtil.repeatSymbol(' ', indent);
    buffer.append(indentString);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      buffer.append(c);
      if (c == '\n') {
        buffer.append(indentString);
      }
    }
    return buffer.toString();
  }
}
