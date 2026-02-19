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
      TokenSet.create(YAMLTokenTypes.SCALAR_KEY),
      TokenSet.create(YAMLTokenTypes.COMMENT),
      YAMLElementTypes.SCALAR_VALUES);
    setMayHaveFileRefsInLiterals(true);
  }
}
