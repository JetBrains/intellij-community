// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.parsing;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.psi.tree.TokenSet;

public class PropertiesWordsScanner extends DefaultWordsScanner {
  public PropertiesWordsScanner() {
    super(new PropertiesLexer(), TokenSet.create(PropertiesTokenTypes.KEY_CHARACTERS),
          PropertiesTokenTypes.COMMENTS, TokenSet.create(PropertiesTokenTypes.VALUE_CHARACTERS));
  }
}
