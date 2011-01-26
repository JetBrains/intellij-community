package com.jetbrains.python.editor;

import com.intellij.codeInsight.editorActions.enter.EnterBetweenBracesHandler;

/**
 * @author yole
 */
public class PyEnterBetweenBracketsHandler extends EnterBetweenBracesHandler {
  // TODO yole, fix compile @Override
  protected boolean isBracePair(char c1, char c2) {
    return c1 == '[' && c2  == ']';
  }
}
