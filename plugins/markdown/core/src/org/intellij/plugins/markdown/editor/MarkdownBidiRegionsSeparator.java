package org.intellij.plugins.markdown.editor;

import com.intellij.openapi.editor.bidi.TokenSetBidiRegionsSeparator;
import com.intellij.psi.tree.TokenSet;

import static org.intellij.plugins.markdown.lang.MarkdownTokenTypes.BACKTICK;
import static org.intellij.plugins.markdown.lang.MarkdownTokenTypes.COLON;
import static org.intellij.plugins.markdown.lang.MarkdownTokenTypes.DOLLAR;
import static org.intellij.plugins.markdown.lang.MarkdownTokenTypes.DOUBLE_QUOTE;
import static org.intellij.plugins.markdown.lang.MarkdownTokenTypes.EMPH;
import static org.intellij.plugins.markdown.lang.MarkdownTokenTypes.EXCLAMATION_MARK;
import static org.intellij.plugins.markdown.lang.MarkdownTokenTypes.GT;
import static org.intellij.plugins.markdown.lang.MarkdownTokenTypes.LBRACKET;
import static org.intellij.plugins.markdown.lang.MarkdownTokenTypes.LPAREN;
import static org.intellij.plugins.markdown.lang.MarkdownTokenTypes.LT;
import static org.intellij.plugins.markdown.lang.MarkdownTokenTypes.RBRACKET;
import static org.intellij.plugins.markdown.lang.MarkdownTokenTypes.RPAREN;
import static org.intellij.plugins.markdown.lang.MarkdownTokenTypes.SINGLE_QUOTE;
import static org.intellij.plugins.markdown.lang.MarkdownTokenTypes.TEXT;
import static org.intellij.plugins.markdown.lang.MarkdownTokenTypes.TILDE;
import static org.intellij.plugins.markdown.lang.MarkdownTokenTypes.WHITE_SPACE;

public final class MarkdownBidiRegionsSeparator extends TokenSetBidiRegionsSeparator {

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
                                                                    BACKTICK,
                                                                    DOLLAR);

  public MarkdownBidiRegionsSeparator() {
    super(PARAGRAPH_CONTENTS);
  }
}
