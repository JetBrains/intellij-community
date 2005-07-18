package com.intellij.formatting;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;

class IndentInside {
  public int whiteSpaces = 0;
  public int tabs = 0;
  private static final Logger LOG = Logger.getInstance("#com.intellij.formatting.FormatProcessor");

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final IndentInside indent = (IndentInside)o;

    if (tabs != indent.tabs) return false;
    return whiteSpaces == indent.whiteSpaces;
  }

  public int hashCode() {
    int result;
    result = whiteSpaces;
    result = 29 * result + tabs;
    return result;
  }

  public int getTabsCount(final CodeStyleSettings.IndentOptions options) {
    final int tabsFromSpaces = whiteSpaces / options.TAB_SIZE;
    return tabs + tabsFromSpaces;
  }

  public int getSpacesCount(final CodeStyleSettings.IndentOptions options) {
    return whiteSpaces + tabs * options.TAB_SIZE;
  }

  static IndentInside getLastLineIndent(final String text) {
    String lastLine = getLastLine(text);
    if (lastLine == null) return new IndentInside();
    return createIndentOn(lastLine);
  }

  static IndentInside createIndentOn(@Nullable final String lastLine) {
    final IndentInside result = new IndentInside();
    if (lastLine == null) {
      return result;
    }
    for (int i = 0; i < lastLine.length(); i++) {
      if (lastLine.charAt(i) == ' ') result.whiteSpaces += 1;
      if (lastLine.charAt(i) == '\t') result.tabs += 1;
    }
    return result;
  }

  @Nullable static String getLastLine(final String text) {
    if (text.endsWith("\n")) return "";
    final LineNumberReader lineNumberReader = new LineNumberReader(new StringReader(text));
    String line;
    String result = null;
    try {
      while ((line = lineNumberReader.readLine()) != null) {
        result = line;
      }
    }
    catch (IOException e) {
      LOG.assertTrue(false);
    }
    return result;
  }
}
