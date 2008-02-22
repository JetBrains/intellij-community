package com.jetbrains.python.editor;

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;

/**
 * @author yole
 */
public class PythonQuoteHandler extends SimpleTokenSetQuoteHandler {
  public PythonQuoteHandler() {
    super(new IElementType[]{PyTokenTypes.STRING_LITERAL});
  }
}
