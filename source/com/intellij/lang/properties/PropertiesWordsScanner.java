package com.intellij.lang.properties;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 31, 2005
 * Time: 9:34:58 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesWordsScanner extends DefaultWordsScanner {
  public PropertiesWordsScanner() {
    super(new PropertiesLexer(), TokenSet.create(new IElementType[] {PropertiesTokenTypes.KEY_CHARACTERS}),
          PropertiesTokenTypes.COMMENTS, TokenSet.create(new IElementType[] {PropertiesTokenTypes.VALUE_CHARACTERS,}));
  }
}
