package org.jetbrains.yaml.lexer;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.yaml.YAMLTokenTypes;

import java.io.Reader;

/**
 * @author oleg
 */
public class YAMLFlexLexer extends MergingLexerAdapter {
  private static final TokenSet TOKENS_TO_MERGE = TokenSet.create(YAMLTokenTypes.TEXT);

  public YAMLFlexLexer() {
    super(new FlexAdapter(new _YAMLLexer((Reader) null)), TOKENS_TO_MERGE);
  }
}
