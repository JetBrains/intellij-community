package com.jetbrains.python.editor;

import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyTokenTypes;

/**
 * @author traff
 */
public class PythonQuoteHandler extends BaseQuoteHandler {
  public PythonQuoteHandler() {
    super(TokenSet.create(PyTokenTypes.STRING_LITERAL), new char[]{'}', ']', ')', ',', ':', ';', ' ', '\t', '\n'});
  }
}
