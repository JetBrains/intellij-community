package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.diagnostic.Logger;

public class HtmlHighlightingLexer extends BaseHtmlLexer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lexer.HtmlHighlightingLexer");

  private static final TokenSet TOKENS_TO_MERGE = TokenSet.create(new IElementType[]{
    XmlTokenType.XML_COMMENT_CHARACTERS,
    XmlTokenType.XML_WHITE_SPACE,
    XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN,
  });

  private static final int EMBEDDED_LEXER_ON = 0x1 << BASE_STATE_SHIFT;
  private static final int EMBEDDED_LEXER_STATE_SHIFT = BASE_STATE_SHIFT + 1;

  private Lexer embeddedLexer;
  private Lexer styleLexer;
  private Lexer scriptLexer;
  private boolean hasNoEmbeddments;
  private static FileType ourStyleFileType;
  private static FileType ourScriptFileType;
  private static final int MAX_EMBEDDED_LEXER_STATE = 16;
  private static final int MAX_EMBEDDED_LEXER_SHIFT = 4;

  // Handles following
  class XmlEmbeddmentHandler implements TokenHandler {
    public void handleElement(Lexer lexer) {
      if (!hasSeenStyle() && !hasSeenScript() || hasNoEmbeddments) return;
      final IElementType tokenType = lexer.getTokenType();

      if ((tokenType==XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN && hasSeenAttribute()) ||
          (tokenType==XmlTokenType.XML_DATA_CHARACTERS && hasSeenTag()) ||
          tokenType==XmlTokenType.XML_COMMENT_CHARACTERS && hasSeenTag()
          ) {
        setEmbeddedLexer();
      }
    }
  }

  public HtmlHighlightingLexer() {
    this(new _HtmlLexer(),true);
  }

  protected HtmlHighlightingLexer(Lexer lexer, boolean caseInsensitive) {
    super(new MergingLexerAdapter(lexer,TOKENS_TO_MERGE),caseInsensitive);

    XmlEmbeddmentHandler value = new XmlEmbeddmentHandler();
    registerHandler(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN,value);
    registerHandler(XmlTokenType.XML_DATA_CHARACTERS,value);
    registerHandler(XmlTokenType.XML_COMMENT_CHARACTERS,value);
  }

  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
    super.start(buffer, startOffset, endOffset, initialState);

    if ((initialState & EMBEDDED_LEXER_ON)!=0) {
      int state = initialState >> EMBEDDED_LEXER_STATE_SHIFT;
      LOG.assertTrue(hasSeenStyle() || hasSeenScript());
      setEmbeddedLexer();
      embeddedLexer.start(buffer,startOffset,endOffset,state);
    } else {
      embeddedLexer = null;
    }
  }

  private void setEmbeddedLexer() {
    Lexer newLexer = null;
    if (hasSeenStyle()) {
      if (styleLexer==null) {
        styleLexer = (ourStyleFileType!=null)? ourStyleFileType.getHighlighter(null).getHighlightingLexer():null;
        if (styleLexer!=null) {
          LOG.assertTrue(styleLexer.getLastState() < MAX_EMBEDDED_LEXER_STATE);
        }
      }

      newLexer = styleLexer;
    } else if (hasSeenScript()) {
      if (scriptLexer==null) {
        scriptLexer = (ourScriptFileType!=null)? ourScriptFileType.getHighlighter(null).getHighlightingLexer():null;
        if (scriptLexer!=null) {
          LOG.assertTrue(scriptLexer.getLastState() < MAX_EMBEDDED_LEXER_STATE);
        }
      }
      newLexer = scriptLexer;
    }

    if (newLexer!=null) {
      embeddedLexer = newLexer;
      embeddedLexer.start(
        getBuffer(),
        HtmlHighlightingLexer.super.getTokenStart(),
        HtmlHighlightingLexer.super.getTokenEnd());
    }
  }

  public void advance() {
    if (embeddedLexer!=null) {
      embeddedLexer.advance();
      if (embeddedLexer.getTokenType()==null) {
        embeddedLexer=null;
      }
    }

    if (embeddedLexer==null) {
      super.advance();
    }
  }

  public IElementType getTokenType() {
    if (embeddedLexer!=null) {
      return embeddedLexer.getTokenType();
    } else {
      IElementType tokenType = super.getTokenType();

      // TODO: fix no DOCTYPE highlighting
      if (tokenType == null) return tokenType;

      if (tokenType==XmlTokenType.XML_NAME) {
        // we need to convert single xml_name for tag name and attribute name into to separate
        // lex types for the highlighting!
        final int state = getState() & BASE_STATE_MASK;

        if (isHtmlTagState(state)) {
          tokenType = XmlTokenType.XML_TAG_NAME;
        }
      } else if (tokenType == XmlTokenType.XML_WHITE_SPACE) {
        tokenType = (getState()!=0)?XmlTokenType.TAG_WHITE_SPACE:XmlTokenType.XML_REAL_WHITE_SPACE;
      }
      return tokenType;
    }
  }

  public int getTokenStart() {
    if (embeddedLexer!=null) {
      return embeddedLexer.getTokenStart();
    } else {
      return super.getTokenStart();
    }
  }

  public int getTokenEnd() {
    if (embeddedLexer!=null) {
      return embeddedLexer.getTokenEnd();
    } else {
      return super.getTokenEnd();
    }
  }

  public static final void registerStyleFileType(FileType fileType) {
    ourStyleFileType = fileType;
  }

  public static void registerScriptFileType(FileType _scriptFileType) {
    ourScriptFileType = _scriptFileType;
  }

  public int getLastState() {
    return super.getLastState() << (((hasNoEmbeddments)?0:MAX_EMBEDDED_LEXER_SHIFT) + 1);
  }

  public int getState() {
    int state = super.getState();

    state |= ((embeddedLexer!=null)?EMBEDDED_LEXER_ON:0);
    if (embeddedLexer!=null) state |= (embeddedLexer.getState() << EMBEDDED_LEXER_STATE_SHIFT);

    return state;
  }

  protected boolean isHtmlTagState(int state) {
    return state == _HtmlLexer.START_TAG_NAME || state == _HtmlLexer.END_TAG_NAME;
  }

  public void setHasNoEmbeddments(boolean hasNoEmbeddments) {
    this.hasNoEmbeddments = hasNoEmbeddments;
  }
}