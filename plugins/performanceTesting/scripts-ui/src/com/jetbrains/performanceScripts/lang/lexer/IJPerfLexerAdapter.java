// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performanceScripts.lang.lexer;

import com.intellij.lexer.FlexAdapter;

public class IJPerfLexerAdapter extends FlexAdapter {

  public IJPerfLexerAdapter() {
    super(new IJPerfLexer(null));
  }
}
