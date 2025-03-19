// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.lang.properties.parsing.PropertiesLexer;
import com.intellij.lexer.LayeredLexer;

public class PropertiesHighlightingLexer extends LayeredLexer {
  public PropertiesHighlightingLexer() {
    super(new PropertiesLexer());
    // no implementation needed in frontback module
  }
}