package com.jetbrains.python.formatter;

import com.intellij.psi.formatter.StaticSymbolWhiteSpaceDefinitionStrategy;

/**
 * @author yole
 */
public class PyWhiteSpaceFormattingStrategy extends StaticSymbolWhiteSpaceDefinitionStrategy {

  public PyWhiteSpaceFormattingStrategy() {
    super('\\');
  }
}
