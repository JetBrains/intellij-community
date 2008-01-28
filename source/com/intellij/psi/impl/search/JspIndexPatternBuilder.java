package com.intellij.psi.impl.search;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerBase;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.jsp.JspTokenType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.util.text.CharSequenceSubSequence;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.CharArrayUtil;

/**
 * @author yole
 */
public class JspIndexPatternBuilder implements IndexPatternBuilder {
  public Lexer getIndexingLexer(final PsiFile file) {
    if (PsiUtil.isInJspFile(file)) {
      final EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(file.getProject(),
                                                                                                           file.getVirtualFile());
      return new LexerEditorHighlighterLexer(highlighter);
    }
    return null;
  }

  public TokenSet getCommentTokenSet(final PsiFile file) {
    final JspFile jspFile = PsiUtil.getJspFile(file);
    TokenSet commentTokens = TokenSet.orSet(JavaIndexPatternBuilder.XML_COMMENT_BIT_SET, StdTokenSets.COMMENT_BIT_SET);
    final ParserDefinition parserDefinition =
      LanguageParserDefinitions.INSTANCE.forLanguage(jspFile.getViewProvider().getTemplateDataLanguage());
    if (parserDefinition != null) {
      commentTokens = TokenSet.orSet(commentTokens, parserDefinition.getCommentTokens());
    }
    return commentTokens;
  }

  public int getCommentStartDelta(final IElementType tokenType) {
    return tokenType == JspTokenType.JSP_COMMENT ? "<%--".length() : 0;
  }

  public int getCommentEndDelta(final IElementType tokenType) {
    return tokenType == JspTokenType.JSP_COMMENT ? "--%>".length() : 0;
  }

  private static class LexerEditorHighlighterLexer extends LexerBase {
    HighlighterIterator iterator;
    CharSequence buffer;
    int start;
    int end;
    private final EditorHighlighter myHighlighter;

    public LexerEditorHighlighterLexer(final EditorHighlighter highlighter) {
      myHighlighter = highlighter;
    }

    public void start(CharSequence buffer, int startOffset, int endOffset, int state) {
      myHighlighter.setText(new CharSequenceSubSequence(this.buffer = buffer, start = startOffset, end = endOffset));
      iterator = myHighlighter.createIterator(0);
    }

    public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
      start(new CharArrayCharSequence(buffer), startOffset, endOffset, initialState);
    }

    public int getState() {
      return 0;
    }

    public IElementType getTokenType() {
      if (iterator.atEnd()) return null;
      return iterator.getTokenType();
    }

    public int getTokenStart() {
      return iterator.getStart();
    }

    public int getTokenEnd() {
      return iterator.getEnd();
    }

    public void advance() {
      iterator.advance();
    }

    public char[] getBuffer() {
      return CharArrayUtil.fromSequence(buffer);
    }

    public CharSequence getBufferSequence() {
      return buffer;
    }

    public int getBufferEnd() {
      return end;
    }
  }
}
