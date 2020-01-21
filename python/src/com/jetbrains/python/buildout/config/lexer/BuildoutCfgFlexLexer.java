// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout.config.lexer;

import com.intellij.lexer.FlexAdapter;

public class BuildoutCfgFlexLexer extends FlexAdapter {
  public BuildoutCfgFlexLexer() {
    super(new _BuildoutCfgFlexLexer(null));
  }
}
