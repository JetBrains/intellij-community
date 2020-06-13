// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lexer;

import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.lang.HtmlScriptContentProvider;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageHtmlScriptContentProvider;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.xml.util.documentation.HtmlDescriptorsTable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

/**
 * @author Maxim.Mossienko
 */
public abstract class BaseHtmlLexer extends DelegateLexer {
  protected static final int BASE_STATE_MASK = 0x3F;
  private static final int SEEN_TAG = 0x40;
  private static final int SEEN_ATTRIBUTE = 0x80;
  private static final int SEEN_CONTENT_TYPE = 0x100;
  private static final int SEEN_STYLESHEET_TYPE = 0x200;
  private static final int SEEN_STYLE_SCRIPT_SHIFT = 10;
  private static final int SEEN_STYLE_SCRIPT_MASK = 0x7 << SEEN_STYLE_SCRIPT_SHIFT;
  protected static final int BASE_STATE_SHIFT = 13;

  protected boolean seenTag;
  protected boolean seenAttribute;
  protected boolean seenStyle;
  protected boolean seenScript;

  private static final char SCRIPT = 1;
  private static final char STYLE = 2;
  private final int[] scriptStyleStack = new int[] {0, 0};

  @Nullable
  protected String scriptType = null;
  @Nullable
  protected String styleType = null;

  protected final boolean caseInsensitive;
  protected boolean seenContentType;
  protected boolean seenStylesheetType;
  private CharSequence cachedBufferSequence;
  private Lexer lexerOfCacheBufferSequence;

  static final TokenSet TOKENS_TO_MERGE = TokenSet.create(XmlTokenType.XML_COMMENT_CHARACTERS, XmlTokenType.XML_WHITE_SPACE, XmlTokenType.XML_REAL_WHITE_SPACE,
                                                          XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, XmlTokenType.XML_DATA_CHARACTERS,
                                                          XmlTokenType.XML_TAG_CHARACTERS);

  public interface TokenHandler {
    void handleElement(Lexer lexer);
  }

  public class XmlNameHandler implements TokenHandler {
    @NonNls private static final String TOKEN_SCRIPT = "script";
    @NonNls private static final String TOKEN_STYLE = "style";
    @NonNls private static final String TOKEN_ON = "on";

    @Override
    public void handleElement(Lexer lexer) {
      final char firstCh = getFirstChar(lexer);

      if (seenScript && !seenTag) {
        seenContentType = false;
        if (((firstCh == 'l' || firstCh == 't') || (caseInsensitive && (firstCh == 'L' || firstCh == 'T')))) {
          @NonNls String name = TreeUtil.getTokenText(lexer);
          seenContentType = Comparing.strEqual("language", name, !caseInsensitive) || Comparing.strEqual("type", name, !caseInsensitive);
          return;
        }
      }
      if (seenStyle && !seenTag) {
        seenStylesheetType = false;
        if (firstCh == 't' || caseInsensitive && firstCh == 'T') {
          seenStylesheetType = Comparing.strEqual(TreeUtil.getTokenText(lexer), "type", !caseInsensitive);
          return;
        }
      }

      if (firstCh !='o' && firstCh !='s' && (!caseInsensitive || (firstCh !='S' && firstCh !='O'))) {
        return; // optimization
      }

      String name = TreeUtil.getTokenText(lexer);
      if (caseInsensitive) name = StringUtil.toLowerCase(name);

      final boolean style = name.equals(TOKEN_STYLE);
      final int state = getState() & BASE_STATE_MASK;
      final boolean script = name.equals(TOKEN_SCRIPT) ||
                       ((name.startsWith(TOKEN_ON) && name.indexOf(':') == -1 && !isHtmlTagState(state) &&
                         HtmlDescriptorsTable.getAttributeDescriptor(name) != null));

      if (style || script) {
        // encountered tag name in end of tag
        if (seenTag) {
          if (isHtmlTagState(state)) {
            seenTag = false;
          }
          return;
        }

        // If we have seenAttribute it means that we need to pop state
        if (seenAttribute) {
          popScriptStyle();
        }
        pushScriptStyle(script, style);

        if (!isHtmlTagState(state)) {
          seenAttribute=true;
        }
      }
    }
  }

  protected char getFirstChar(Lexer lexer) {
    final CharSequence buffer;
    if (lexerOfCacheBufferSequence == lexer) {
      buffer = cachedBufferSequence;
    } else {
      cachedBufferSequence = lexer.getBufferSequence();
      buffer = cachedBufferSequence;
      lexerOfCacheBufferSequence = lexer;
    }
    return buffer.charAt(lexer.getTokenStart());
  }

  class XmlAttributeValueEndHandler implements TokenHandler {
    @Override
    public void handleElement(Lexer lexer) {
      if (seenAttribute) {
        popScriptStyle();
        seenAttribute = false;
      }
      seenContentType = false;
      seenStylesheetType = false;
    }
  }

  protected void pushScriptStyle(boolean script, boolean style) {
    int position = scriptStyleStack[0] == 0 ? 0 : 1;
    scriptStyleStack[position] = script ? SCRIPT :
                                 style ? STYLE :
                                 0;
    seenStyle = style;
    seenScript = script;
  }

  protected void popScriptStyle() {
    int position = scriptStyleStack[1] == 0 ? 0 : 1;
    scriptStyleStack[position] = 0;
    seenStyle = scriptStyleStack[0] == STYLE;
    seenScript = scriptStyleStack[0] == SCRIPT;
  }

  class XmlAttributeValueHandler implements TokenHandler {
    @Override
    public void handleElement(Lexer lexer) {
      if (seenContentType && seenScript && !seenAttribute) {
        @NonNls String mimeType = TreeUtil.getTokenText(lexer);
        scriptType = caseInsensitive ? StringUtil.toLowerCase(mimeType) : mimeType;
      }
      if (seenStylesheetType && seenStyle && !seenAttribute) {
        @NonNls String type = TreeUtil.getTokenText(lexer).trim();
        styleType = caseInsensitive ? StringUtil.toLowerCase(type) : type;
      }
    }
  }

  @Nullable
  protected Language getScriptLanguage() {
    Collection<Language> instancesByMimeType = Language.findInstancesByMimeType(scriptType != null ? scriptType.trim() : null);
    return instancesByMimeType.isEmpty() ? null : instancesByMimeType.iterator().next();
  }

  @Nullable
  protected Language getStyleLanguage() {
    Language cssLanguage = Language.findLanguageByID("CSS");
    if (cssLanguage != null && styleType != null && !"text/css".equals(styleType)) {
      for (Language language : cssLanguage.getDialects()) {
        for (String mimeType : language.getMimeTypes()) {
          if (styleType.equals(mimeType)) {
            return language;
          }
        }
      }
    }
    return cssLanguage;
  }

  @Nullable
  protected IElementType getCurrentScriptElementType() {
    HtmlScriptContentProvider scriptContentProvider = findScriptContentProvider(scriptType);
    return scriptContentProvider == null ? null : scriptContentProvider.getScriptElementType();
  }

  @Nullable
  protected IElementType getCurrentStylesheetElementType() {
    Language language = getStyleLanguage();
    if (language != null) {
      for (EmbeddedTokenTypesProvider provider : EmbeddedTokenTypesProvider.EXTENSION_POINT_NAME.getExtensionList()) {
        IElementType elementType = provider.getElementType();
        if (language.is(elementType.getLanguage())) {
          return elementType;
        }
      }
    }
    return null;
  }

  @Nullable
  protected HtmlScriptContentProvider findScriptContentProvider(@Nullable String mimeType) {
    if (StringUtil.isEmpty(mimeType)) {
      Language defaultLanguage = Language.findLanguageByID("JavaScript");
      return defaultLanguage != null ? LanguageHtmlScriptContentProvider.getScriptContentProvider(defaultLanguage) : null;
    }
    Collection<Language> instancesByMimeType = Language.findInstancesByMimeType(mimeType.trim());
    if (instancesByMimeType.isEmpty() && mimeType.contains("template")) {
      instancesByMimeType = Collections.singletonList(HTMLLanguage.INSTANCE);
    }
    for (Language language : instancesByMimeType) {
      HtmlScriptContentProvider scriptContentProvider = LanguageHtmlScriptContentProvider.getScriptContentProvider(language);
      if (scriptContentProvider != null) {
        return scriptContentProvider;
      }
    }
    return null;
  }

  class XmlTagClosedHandler implements TokenHandler {
    @Override
    public void handleElement(Lexer lexer) {
      if (seenAttribute) {
        popScriptStyle();
        seenAttribute=false;
      } else {
        if (seenStyle || seenScript) {
          seenTag=true;
        }
      }
    }
  }

  class XmlTagEndHandler implements TokenHandler {
    @Override
    public void handleElement(Lexer lexer) {
      popScriptStyle();
      seenAttribute=false;
      seenContentType=false;
      seenStylesheetType=false;
      scriptType = null;
      styleType = null;
    }
  }

  private final HashMap<IElementType,TokenHandler> tokenHandlers = new HashMap<>();

  protected BaseHtmlLexer(Lexer _baseLexer, boolean _caseInsensitive)  {
    super(_baseLexer);
    caseInsensitive = _caseInsensitive;

    XmlNameHandler value = new XmlNameHandler();
    tokenHandlers.put(XmlTokenType.XML_NAME,value);
    tokenHandlers.put(XmlTokenType.XML_TAG_NAME,value);
    tokenHandlers.put(XmlTokenType.XML_TAG_END,new XmlTagClosedHandler());
    tokenHandlers.put(XmlTokenType.XML_END_TAG_START,new XmlTagEndHandler());
    tokenHandlers.put(XmlTokenType.XML_EMPTY_ELEMENT_END,new XmlTagEndHandler());
    tokenHandlers.put(XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER,new XmlAttributeValueEndHandler());
    tokenHandlers.put(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN,new XmlAttributeValueHandler());
  }

  protected void registerHandler(IElementType elementType, TokenHandler value) {
    final TokenHandler tokenHandler = tokenHandlers.get(elementType);

    if (tokenHandler != null) {
      final TokenHandler newHandler = value;
      value = new TokenHandler() {
        @Override
        public void handleElement(final Lexer lexer) {
          tokenHandler.handleElement(lexer);
          newHandler.handleElement(lexer);
        }
      };
    }

    tokenHandlers.put(elementType,value);
  }

  @Override
  public void start(@NotNull final CharSequence buffer, final int startOffset, final int endOffset, final int initialState) {
    initState(initialState);
    super.start(buffer, startOffset, endOffset, initialState & BASE_STATE_MASK);
  }

  private void initState(final int initialState) {
    seenTag = (initialState & SEEN_TAG)!=0;
    seenAttribute = (initialState & SEEN_ATTRIBUTE)!=0;
    seenContentType = (initialState & SEEN_CONTENT_TYPE) != 0;
    seenStylesheetType = (initialState & SEEN_STYLESHEET_TYPE) != 0;
    decodeScriptStack(((initialState & SEEN_STYLE_SCRIPT_MASK) >> SEEN_STYLE_SCRIPT_SHIFT));
    int position = scriptStyleStack[1] == 0 ? 0 : 1;
    seenStyle = scriptStyleStack[position] == STYLE;
    seenScript = scriptStyleStack[position] == SCRIPT;
    lexerOfCacheBufferSequence = null;
    cachedBufferSequence = null;
  }

  protected int skipToTheEndOfTheEmbeddment() {
    Lexer base = getDelegate();
    int initialStart = base.getTokenStart();
    int tokenEnd = base.getTokenEnd();
    int lastState = 0;
    int lastStart = 0;

    final CharSequence buf = base.getBufferSequence();
    final char[] bufArray = CharArrayUtil.fromSequenceWithoutCopying(buf);

    if (seenTag) {
      FoundEnd:
      while(true) {
        FoundEndOfTag:
        while(base.getTokenType() != XmlTokenType.XML_END_TAG_START) {
          if (base.getTokenType() == XmlTokenType.XML_COMMENT_CHARACTERS) {
            // we should terminate on first occurence of </
            final int end = base.getTokenEnd();

            for(int i = base.getTokenStart(); i < end; ++i) {
              if ((bufArray != null ? bufArray[i ]:buf.charAt(i)) == '<' &&
                  i + 1 < end &&
                  (bufArray != null ? bufArray[i+1]:buf.charAt(i+1)) == '/') {
                tokenEnd = i;
                lastStart = i - 1;
                lastState = 0;

                break FoundEndOfTag;
              }
            }
          }

          lastState = base.getState();
          tokenEnd = base.getTokenEnd();
          lastStart = base.getTokenStart();
          if (tokenEnd == getBufferEnd()) break FoundEnd;
          base.advance();
        }

        // check if next is script
        if (base.getTokenType() != XmlTokenType.XML_END_TAG_START) { // we are inside comment
          base.start(buf,lastStart+1,getBufferEnd(),lastState);
          base.getTokenType();
        }
        base.advance();

        while(XmlTokenType.WHITESPACES.contains(base.getTokenType())) {
          base.advance();
        }

        if (base.getTokenType() == XmlTokenType.XML_NAME) {
          String name = TreeUtil.getTokenText(base);
          if (caseInsensitive) name = StringUtil.toLowerCase(name);

          if(endOfTheEmbeddment(name)) {
            break; // really found end
          }
        }
      }

      if (lastStart < initialStart) {
        // empty embeddment
        base.start(buf, initialStart, getBufferEnd(), lastState);
        TokenHandler tokenHandler = tokenHandlers.get(base.getTokenType());
        if (tokenHandler != null) tokenHandler.handleElement(this);
      } else {
        base.start(buf, lastStart, getBufferEnd(), lastState);
        base.getTokenType();
      }
    } else if (seenAttribute) {
      while(true) {
        if (!isValidAttributeValueTokenType(base.getTokenType())) break;

        tokenEnd = base.getTokenEnd();
        lastState = base.getState();
        lastStart = base.getTokenStart();

        if (tokenEnd == getBufferEnd()) break;
        base.advance();
      }

      base.start(buf,lastStart,getBufferEnd(),lastState);
      base.getTokenType();
    }
    return tokenEnd;
  }

  protected boolean endOfTheEmbeddment(String name) {
    return (hasSeenScript() && XmlNameHandler.TOKEN_SCRIPT.equals(name)) ||
           (hasSeenStyle() && XmlNameHandler.TOKEN_STYLE.equals(name)) ||
           CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED.equalsIgnoreCase(name);
  }

  protected boolean isValidAttributeValueTokenType(final IElementType tokenType) {
    return tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN ||
           tokenType == XmlTokenType.XML_ENTITY_REF_TOKEN ||
           tokenType == XmlTokenType.XML_CHAR_ENTITY_REF;
  }

  @Override
  public void advance() {
    super.advance();
    IElementType type = getDelegate().getTokenType();
    TokenHandler tokenHandler = tokenHandlers.get(type);
    if (tokenHandler!=null) tokenHandler.handleElement(this);
  }


  @Override
  public int getState() {
    int state = super.getState();

    state |= ((seenTag)?SEEN_TAG:0);
    state |= ((seenAttribute)?SEEN_ATTRIBUTE:0);
    state |= ((seenContentType)?SEEN_CONTENT_TYPE:0);
    state |= ((seenStylesheetType)?SEEN_STYLESHEET_TYPE:0);
    state |= encodeScriptStack() << SEEN_STYLE_SCRIPT_SHIFT;

    return state;
  }

  private int encodeScriptStack() {
    if (scriptStyleStack[1] == 0) {
      return scriptStyleStack[0];
    }
    if (scriptStyleStack[0] == 0) {
      throw new IllegalStateException();
    }
    return scriptStyleStack[0] * 2 + scriptStyleStack[1];
  }

  private void decodeScriptStack(int value) {
    if (value <= 2) {
      scriptStyleStack[0] = value;
      scriptStyleStack[1] = 0;
    }
    else {
      value -= 3;
      scriptStyleStack[0] = (value / 2) + 1;
      scriptStyleStack[1] = (value % 2) + 1;
    }
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

  protected int getBaseStateShift() {
    return BASE_STATE_SHIFT;
  }

  protected abstract boolean isHtmlTagState(int state);
}
