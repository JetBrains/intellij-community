package com.jetbrains.python.psi;

import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.lexer.PythonLexer;
import org.jetbrains.annotations.NotNull;

public class PyReparseableSingleQuotedStringTokenType extends PyReparseableTokenTypeWithSimpleCheck {

  public PyReparseableSingleQuotedStringTokenType(@NotNull String debugName) {
    super(debugName);
  }

  @Override
  public boolean isReparseable(@NotNull String newText) {
    if (!isSingleQuotedString(newText)) { // fail-fast
      return false;
    }
    PythonLexer lexer = new PythonLexer();
    lexer.start(newText);
    IElementType firstTokenType = lexer.getTokenType();
    lexer.advance();
    IElementType nextTokenType = lexer.getTokenType();
    return firstTokenType == PyTokenTypes.DOCSTRING && nextTokenType == null;
  }

  private static boolean isSingleQuotedString(@NotNull String newText) {
    return newText.length() > 1
           && ((newText.startsWith("\"") && newText.endsWith("\""))
               || (newText.startsWith("'") && newText.endsWith("'")))
           && !(newText.startsWith("'''") || newText.startsWith("\"\"\""))
           && !(newText.endsWith("'''") || newText.endsWith("\"\"\""));
  }
}
