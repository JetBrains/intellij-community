package org.jetbrains.yaml;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.psi.tree.TokenSet;

/**
 * @author shalupov
 */
public class YAMLWordsScanner extends DefaultWordsScanner {
  public YAMLWordsScanner() {
    super(
      YAMLParserDefinition.createLexer(),
      TokenSet.create(YAMLTokenTypes.SCALAR_KEY),
      TokenSet.create(YAMLTokenTypes.COMMENT),
      YAMLElementTypes.SCALAR_VALUES);
    setMayHaveFileRefsInLiterals(true);
  }
}
