package com.jetbrains.python.lexer;

import com.intellij.psi.tree.TokenSet;

import java.io.Reader;

/**
 * @author yole
 */
public class PythonIndentingLexer extends PythonIndentingProcessor {
  public PythonIndentingLexer() {
    super(new _PythonLexer((Reader)null), TokenSet.EMPTY);
  }
}
