package org.jetbrains.yaml;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.yaml.lexer.YAMLFlexLexer;

/**
 * @author shalupov
 */
public class YAMLWordsScanner extends DefaultWordsScanner {
  public YAMLWordsScanner() {
    super(
      new YAMLFlexLexer(),
      TokenSet.create(
        YAMLElementTypes.SCALAR_TEXT_VALUE,
        YAMLElementTypes.SCALAR_PLAIN_VALUE,
        YAMLElementTypes.SCALAR_QUOTED_STRING),
      TokenSet.create(YAMLTokenTypes.COMMENT),
      TokenSet.EMPTY);
    setMayHaveFileRefsInLiterals(true);
  }
}
