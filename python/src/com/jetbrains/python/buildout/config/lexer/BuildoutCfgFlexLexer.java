package com.jetbrains.python.buildout.config.lexer;

import com.intellij.lexer.FlexAdapter;

import java.io.Reader;

/**
 * @author traff
 */
public class BuildoutCfgFlexLexer extends FlexAdapter {
  public BuildoutCfgFlexLexer() {
    super(new _BuildoutCfgFlexLexer((Reader)null));
  }
}
