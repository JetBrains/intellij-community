package com.intellij.lang.properties.parsing;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lang.properties.parsing._PropertiesLexer;

import java.io.Reader;

/**
 * @author max
 */
public class PropertiesLexer extends FlexAdapter {
  public PropertiesLexer() {
    super(new _PropertiesLexer((Reader)null));
  }
}
