package org.jetbrains.yaml.lexer;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLTokenTypes;

import java.io.Reader;

/**
 * @author oleg
 */
public class YAMLFlexLexer extends MergingLexerAdapter {
  private static final TokenSet TOKENS_TO_MERGE = TokenSet.create(YAMLTokenTypes.TEXT);

  public YAMLFlexLexer() {
    super(new MyFlexAdapter(new _YAMLLexer((Reader) null)), TOKENS_TO_MERGE);
  }

  private static class MyFlexAdapter extends FlexAdapter {

    private boolean myStateCleanliness = false;

    public MyFlexAdapter(_YAMLLexer flex) {
      super(flex);
    }

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
      super.start(buffer, startOffset, endOffset, initialState);
      ((_YAMLLexer)getFlex()).cleanMyState();
    }

    @Override
    public int getState() {
      final int state = super.getState();
      if (state != 0 || myStateCleanliness) {
        return state;
      }
      return 239;
    }

    @Override
    protected void locateToken() {
      myStateCleanliness = ((_YAMLLexer)getFlex()).isCleanState();
      super.locateToken();
    }
  }
}
