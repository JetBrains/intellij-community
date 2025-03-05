// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.parsing;

import com.intellij.lexer.FlexAdapter;

public class PropertiesLexer extends FlexAdapter {
  public PropertiesLexer() {
    super(new _PropertiesLexer(null));
  }
}
