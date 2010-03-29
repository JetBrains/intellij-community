package com.jetbrains.python.lexer;

import com.intellij.lexer.FlexAdapter;

import java.io.Reader;

/**
 * @author yole
 */
public class PythonLexer extends FlexAdapter {
  public PythonLexer() {
    super(new _PythonLexer((Reader)null));
  }

  public _PythonLexer getFlex() {
    return (_PythonLexer)super.getFlex();
  }
}
