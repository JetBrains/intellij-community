// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.PropertiesHighlightingLexer.PropertiesStringLiteralLexer;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lexer.DummyLexer;
import com.intellij.lexer.LayeredLexer;
import com.intellij.psi.tree.IElementType;

public class PropertiesValueHighlightingLexer extends LayeredLexer {
  public PropertiesValueHighlightingLexer() {
      super(new DummyLexer(PropertiesTokenTypes.VALUE_CHARACTERS));

    registerSelfStoppingLayer(new PropertiesStringLiteralLexer(PropertiesTokenTypes.VALUE_CHARACTERS),
                              new IElementType[]{PropertiesTokenTypes.VALUE_CHARACTERS},
                              IElementType.EMPTY_ARRAY);
  }
}
