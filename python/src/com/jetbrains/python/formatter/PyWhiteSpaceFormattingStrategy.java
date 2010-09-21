package com.jetbrains.python.formatter;

import com.intellij.formatting.WhiteSpaceFormattingStrategy;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyWhiteSpaceFormattingStrategy implements WhiteSpaceFormattingStrategy {
  @Override
  public int check(@NotNull CharSequence text, int start, int end) {
    for (int i = start; i < end; i++) {
      char c = text.charAt(i);
      if (c != ' ' && c != '\t' && c != '\n' && c != '\\') {
        return i;
      }
    }
    return end;
  }
}
