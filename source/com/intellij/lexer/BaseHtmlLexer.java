package com.intellij.lexer;

import com.intellij.psi.impl.source.parsing.ParseUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;

import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Oct 7, 2004
 * Time: 2:29:06 PM
 * To change this template use File | Settings | File Templates.
 */
abstract class BaseHtmlLexer implements Lexer {
  private Lexer baseLexer;
  protected static final int BASE_STATE_MASK = 0xF;
  private static final int SEEN_STYLE = 0x10;
  private static final int SEEN_TAG = 0x20;
  private static final int SEEN_SCRIPT = 0x40;
  private static final int SEEN_ATTRIBUTE = 0x80;
  protected static final int BASE_STATE_SHIFT = 8;

  private boolean seenTag;
  private boolean seenAttribute;
  private boolean seenStyle;
  private boolean seenScript;
  private boolean caseInsensitive;

  interface TokenHandler {
    void handleElement(Lexer lexer);
  }

  class XmlNameHandler implements TokenHandler {

    public void handleElement(Lexer lexer) {
      final char ch = lexer.getBuffer()[lexer.getTokenStart()];

      if (ch!='s' && ch!='o' &&
          (!caseInsensitive || (ch!='S' && ch!='O') )
          ) {
        return; // optimization
      }

      String name = ParseUtil.getTokenText(lexer);
      if (caseInsensitive) name = name.toLowerCase();

      boolean style = name.equals("style");
      boolean script = name.equals("script") || name.startsWith("on");

      if (style || script) {
        // encountered tag name in end of tag
        if (seenTag) {
          seenTag = false;
          return;
        }

        seenStyle = style;
        seenScript = script;

        final int state = getState() & BASE_STATE_MASK;

        if (!isHtmlTagState(state)) {
          seenAttribute=true;
        }
      } else if (seenAttribute) {
        seenStyle = false; // does this is valid branch?
        seenScript = false;
        seenAttribute=false;
      }
    }
  }

  class XmlAttributeValueEndHandler implements TokenHandler {
    public void handleElement(Lexer lexer) {
      if (seenAttribute) {
        seenStyle = false;
        seenScript = false;
      }
    }
  }

  class XmlTagClosedHandler implements TokenHandler {
    public void handleElement(Lexer lexer) {
      if (seenAttribute) {
        seenScript=false;
        seenStyle=false;

        seenAttribute=false;
      } else {
        if (seenStyle || seenScript) {
          seenTag=true;
        }
      }
    }
  }

  class XmlTagEndHandler implements TokenHandler {
    public void handleElement(Lexer lexer) {
      seenStyle=false;
      seenScript=false;
    }
  }

  private HashMap<IElementType,TokenHandler> tokenHandlers = new HashMap<IElementType, TokenHandler>();

  protected BaseHtmlLexer(Lexer _baseLexer, boolean _caseInsensitive)  {
    baseLexer = _baseLexer;
    caseInsensitive = _caseInsensitive;

    XmlNameHandler value = new XmlNameHandler();
    tokenHandlers.put(XmlTokenType.XML_NAME,value);
    tokenHandlers.put(XmlTokenType.XML_TAG_NAME,value);
    tokenHandlers.put(XmlTokenType.XML_TAG_END,new XmlTagClosedHandler());
    tokenHandlers.put(XmlTokenType.XML_END_TAG_START,new XmlTagEndHandler());
    tokenHandlers.put(XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER,new XmlAttributeValueEndHandler());
  }

  protected void registerHandler(IElementType elementType, TokenHandler value) {
    tokenHandlers.put(elementType,value);
  }

  public void start(char[] buffer) {
    start(buffer,0, buffer.length);
  }

  public void start(char[] buffer, int startOffset, int endOffset) {
    start(buffer,startOffset,endOffset,0);
  }

  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
    seenScript = (initialState & SEEN_SCRIPT)!=0;
    seenStyle = (initialState & SEEN_STYLE)!=0;
    seenTag = (initialState & SEEN_TAG)!=0;
    seenAttribute = (initialState & SEEN_ATTRIBUTE)!=0;

    baseLexer.start(buffer, startOffset, endOffset, initialState & BASE_STATE_MASK);
  }

  public void advance() {
    baseLexer.advance();
    IElementType type = getTokenType();
    TokenHandler tokenHandler = tokenHandlers.get(type);
    if (tokenHandler!=null) tokenHandler.handleElement(this);
  }

  public char[] getBuffer() {
    return baseLexer.getBuffer();
  }

  public int getBufferEnd() {
    return baseLexer.getBufferEnd();
  }

  public int getSmartUpdateShift() {
    return baseLexer.getSmartUpdateShift();
  }

  public Object clone() {
    try {
      BaseHtmlLexer clone = (BaseHtmlLexer)super.clone();
      clone.baseLexer = (Lexer)baseLexer.clone();
    }
    catch (CloneNotSupportedException e) {
    }
    return null;
  }

  public IElementType getTokenType() {
    return baseLexer.getTokenType();
  }

  public int getTokenStart() {
    return baseLexer.getTokenStart();
  }

  public int getTokenEnd() {
    return baseLexer.getTokenEnd();
  }

  public int getLastState() {
    return baseLexer.getLastState() << 4;
  }

  public int getState() {
    int state = baseLexer.getState();

    state |= ((seenScript)?SEEN_SCRIPT:0);
    state |= ((seenTag)?SEEN_TAG:0);
    state |= ((seenStyle)?SEEN_STYLE:0);
    state |= ((seenAttribute)?SEEN_ATTRIBUTE:0);

    return state;
  }

  protected final boolean hasSeenStyle() {
    return seenStyle;
  }

  protected final boolean hasSeenAttribute() {
    return seenAttribute;
  }

  protected final boolean hasSeenTag() {
    return seenTag;
  }

  protected boolean hasSeenScript() {
    return seenScript;
  }

  protected abstract boolean isHtmlTagState(int state);
}
