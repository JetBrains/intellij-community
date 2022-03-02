package org.intellij.plugins.markdown.editor;

import com.intellij.openapi.editor.bidi.TokenSetBidiRegionsSeparator;
import com.intellij.psi.tree.TokenSet;

import static org.intellij.plugins.markdown.lang.MarkdownTokenTypes.*;

public class MarkdownBidiRegionsSeparator extends TokenSetBidiRegionsSeparator {

  public static final TokenSet PARAGRAPH_CONTENTS = TokenSet.create(TEXT,
                                                                    WHITE_SPACE,
                                                                    SINGLE_QUOTE,
                                                                    DOUBLE_QUOTE,
                                                                    LPAREN,
                                                                    RPAREN,
                                                                    LBRACKET,
                                                                    RBRACKET,
                                                                    LT,
                                                                    GT,
                                                                    COLON,
                                                                    EXCLAMATION_MARK,
                                                                    EMPH,
                                                                    TILDE,
                                                                    BACKTICK);

  public MarkdownBidiRegionsSeparator() {
    super(PARAGRAPH_CONTENTS);
  }
}
