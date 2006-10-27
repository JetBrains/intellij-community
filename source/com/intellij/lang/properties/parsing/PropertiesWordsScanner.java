package com.intellij.lang.properties.parsing;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.psi.tree.TokenSet;

/**
 * @author max
 */
public class PropertiesWordsScanner extends DefaultWordsScanner {
  public PropertiesWordsScanner() {
    super(new PropertiesLexer(), TokenSet.create(PropertiesTokenTypes.KEY_CHARACTERS),
          PropertiesTokenTypes.COMMENTS, TokenSet.create(PropertiesTokenTypes.VALUE_CHARACTERS));
  }
}
