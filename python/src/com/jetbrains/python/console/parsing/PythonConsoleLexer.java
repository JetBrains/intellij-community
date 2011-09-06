package com.jetbrains.python.console.parsing;

import com.google.common.collect.ImmutableMap;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.lexer.PythonIndentingLexer;
import com.jetbrains.python.psi.PyElementType;

import java.util.Map;

/**
 * @author traff
 */
public class PythonConsoleLexer extends PythonIndentingLexer {
  private final static Map<String, PyElementType> SPECIAL_IPYTHON_SYMBOLS = ImmutableMap.of("?", PyConsoleTokenTypes
    .QUESTION_MARK, "!", PyConsoleTokenTypes.PLING);

  @Override
  /**
   * Treats special symbols used in IPython console
   */
  public IElementType getTokenType() {
    IElementType type = super.getTokenType();
    if (type == PyTokenTypes.BAD_CHARACTER && isSpecialSymbols(getTokenText())) {
      type = getElementType(getTokenText());
    }

    return type;
  }

  public static PyElementType getElementType(String token) {
    return SPECIAL_IPYTHON_SYMBOLS.get(token);
  }

  public static boolean isSpecialSymbols(String token) {
    return SPECIAL_IPYTHON_SYMBOLS.containsKey(token);
  }
}
