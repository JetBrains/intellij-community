package com.jetbrains.python.buildout.config.lexer;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.buildout.config.BuildoutCfgTokenTypes;

import java.io.Reader;

/**
 * @author traff
 */
public class BuildoutCfgFlexLexer extends FlexAdapter {
  public BuildoutCfgFlexLexer() {
    super(new _BuildoutCfgFlexLexer((Reader)null));
  }
}
