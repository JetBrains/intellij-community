// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.lexer;

import com.intellij.lexer.FlexAdapter;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PythonLexer extends FlexAdapter {
  public PythonLexer() {
    super(new _PythonLexer(null));
  }

  @Override
  public _PythonLexer getFlex() {
    return (_PythonLexer)super.getFlex();
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    super.start(buffer, startOffset, endOffset, initialState);
    getFlex().fStringHelper.reset(startOffset);
  }
}
