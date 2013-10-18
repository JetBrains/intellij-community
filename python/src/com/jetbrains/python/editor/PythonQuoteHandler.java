package com.jetbrains.python.editor;

import com.jetbrains.python.PyTokenTypes;

/**
 * @author traff
 */
public class PythonQuoteHandler extends BaseQuoteHandler {
  public PythonQuoteHandler() {
    super(PyTokenTypes.STRING_NODES, new char[]{'}', ']', ')', ',', ':', ';', ' ', '\t', '\n'});
  }
}
