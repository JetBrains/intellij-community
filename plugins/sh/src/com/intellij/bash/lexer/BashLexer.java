package com.intellij.bash.lexer;

import com.intellij.bash.BashVersion;
import com.intellij.lexer.FlexAdapter;
import com.intellij.psi.tree.TokenSet;

public class BashLexer extends MergingLexer implements BashTokenTypes {
  public BashLexer() {
    this(BashVersion.V4);
  }

  public BashLexer(BashVersion bashVersion) {
    super(new FlexAdapter(
            new _BashLexer(bashVersion, null) {
              @Override
              public void reset(CharSequence buffer, int start, int end, int initialState) {
                onReset();
                super.reset(buffer, start, end, initialState);
              }
            }),
        MergeTuple.create(TokenSet.create(STRING_DATA), STRING_CONTENT),
        MergeTuple.create(TokenSet.create(HEREDOC_LINE), HEREDOC_CONTENT));
  }
}
