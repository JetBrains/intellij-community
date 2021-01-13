// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lexer;

import com.intellij.html.embedding.HtmlEmbeddedContentProvider;
import com.intellij.html.embedding.HtmlEmbeddedContentSupport;
import com.intellij.html.embedding.HtmlEmbedment;
import com.intellij.lang.HtmlScriptContentProvider;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.psi.xml.XmlTokenType.*;

public abstract class BaseHtmlLexer extends DelegateLexer {
  protected static final int BASE_STATE_MASK = 0x3F;
  private static final int CONTENT_PROVIDER_HAS_STATE = 0x40;
  private static final int IS_WITHIN_TAG_STATE = 0x80;
  protected static final int BASE_STATE_SHIFT = 9;


  protected static final TokenSet ATTRIBUTE_EMBEDMENT_TOKENS = TokenSet.create(XML_ATTRIBUTE_VALUE_TOKEN, XML_ENTITY_REF_TOKEN, XML_CHAR_ENTITY_REF);
  protected static final TokenSet TAG_EMBEDMENT_START_TOKENS = TokenSet.create(
    XML_DATA_CHARACTERS, XML_CDATA_START, XML_COMMENT_START, XML_START_TAG_START, XML_REAL_WHITE_SPACE, XML_END_TAG_START,
    TokenType.WHITE_SPACE, XML_ENTITY_REF_TOKEN, XML_CHAR_ENTITY_REF
  );

  private boolean isWithinTag = false;

  protected final boolean caseInsensitive;
  private final List<HtmlEmbeddedContentProvider> myEmbeddedContentProviders;
  private final TokenSet myTagEmbedmentStartTokens;
  private final TokenSet myAttributeEmbedmentTokens;
  private HtmlEmbedment myHtmlEmbedmentInfo;

  static final TokenSet TOKENS_TO_MERGE =
    TokenSet.create(XML_COMMENT_CHARACTERS, XML_WHITE_SPACE, XML_REAL_WHITE_SPACE,
                    XML_ATTRIBUTE_VALUE_TOKEN, XML_DATA_CHARACTERS,
                    XML_TAG_CHARACTERS);

  protected BaseHtmlLexer(@NotNull Lexer _baseLexer, boolean _caseInsensitive) {
    super(_baseLexer);
    caseInsensitive = _caseInsensitive;
    List<HtmlEmbeddedContentSupport> supports = getEmbeddedContentSupportList();
    myEmbeddedContentProviders = supports.stream()
      .map(factory -> factory.createEmbeddedContentProviders(this))
      .flatMap(Collection::stream)
      .filter(this::acceptEmbeddedContentProvider)
      .collect(Collectors.toUnmodifiableList());
    myTagEmbedmentStartTokens = createTagEmbedmentStartTokenSet();
    myAttributeEmbedmentTokens = createAttributeEmbedmentTokenSet();
  }

  public boolean isCaseInsensitive() {
    return caseInsensitive;
  }

  @Override
  public void start(@NotNull final CharSequence buffer, final int startOffset, final int endOffset, final int initialState) {
    super.start(buffer, startOffset, endOffset, initialState & BASE_STATE_MASK);
    if ((initialState & CONTENT_PROVIDER_HAS_STATE) != 0) {
      throw new IllegalStateException(
        "Cannot restore HTML Lexer to a position, in which an embedded content provider has state. Use restoreLocation() method.");
    }
    isWithinTag = (initialState & IS_WITHIN_TAG_STATE) != 0;
    myHtmlEmbedmentInfo = null;
    myEmbeddedContentProviders.forEach(provider -> provider.restoreState(null));
    broadcastToken();
  }

  @Override
  public void advance() {
    if (myHtmlEmbedmentInfo != null) {
      myDelegate.start(myDelegate.getBufferSequence(), myHtmlEmbedmentInfo.getRange().getEndOffset(),
                       myDelegate.getBufferEnd(), myHtmlEmbedmentInfo.getBaseLexerState());
    }
    else {
      super.advance();
    }
    broadcastToken();
    myHtmlEmbedmentInfo = null;
  }

  protected @NotNull TokenSet createTagEmbedmentStartTokenSet() {
    return TAG_EMBEDMENT_START_TOKENS;
  }

  protected @NotNull TokenSet createAttributeEmbedmentTokenSet() {
    return ATTRIBUTE_EMBEDMENT_TOKENS;
  }

  private void broadcastToken() {
    IElementType type = myDelegate.getTokenType();
    if (type != null) {
      TextRange textRange = new TextRange(myDelegate.getTokenStart(), myDelegate.getTokenEnd());
      myEmbeddedContentProviders.forEach(provider -> provider.handleToken(type, textRange));
      if (type == XML_NAME && isHtmlTagState(super.getState())) {
        isWithinTag = true;
      }
      else if (type == XML_TAG_END || type == XML_EMPTY_ELEMENT_END) {
        isWithinTag = false;
      }
    }
  }

  @Override
  public int getState() {
    int state = super.getState();
    if (myHtmlEmbedmentInfo != null
        || ContainerUtil.find(myEmbeddedContentProviders, provider -> provider.hasState()) != null) {
      state |= CONTENT_PROVIDER_HAS_STATE;
    }
    if (isWithinTag) {
      state |= IS_WITHIN_TAG_STATE;
    }
    return state;
  }

  protected @Nullable HtmlEmbedment acquireHtmlEmbedmentInfo() {
    IElementType type = myDelegate.getTokenType();
    if (type != null) {
      for (HtmlEmbeddedContentProvider provider : myEmbeddedContentProviders) {
        HtmlEmbedment embedment = provider.createEmbedment(type);
        if (embedment != null) {
          myEmbeddedContentProviders.forEach(HtmlEmbeddedContentProvider::clearEmbedment);
          return embedment;
        }
      }
    }
    return null;
  }

  @Override
  public @NotNull LexerPosition getCurrentPosition() {
    return new HtmlLexerPosition(getTokenStart(), getState() & (~CONTENT_PROVIDER_HAS_STATE), myDelegate.getCurrentPosition(),
                                 myEmbeddedContentProviders.stream()
                                   .map(provider -> new Pair<>(provider, provider.getState()))
                                   .filter(pair -> pair.second != null)
                                   .collect(Collectors.toMap(p -> p.first, p -> p.second)));
  }

  @Override
  public void restore(@NotNull LexerPosition position) {
    super.restore(position);
    myDelegate.restore(((HtmlLexerPosition)position).myDelegateLexerPosition);
    Map<HtmlEmbeddedContentProvider, Object> providersState = ((HtmlLexerPosition)position).myProvidersState;
    myEmbeddedContentProviders.forEach(provider -> provider.restoreState(providersState.get(provider)));
  }

  protected void skipEmbedment(@NotNull HtmlEmbedment embedment) {
    myHtmlEmbedmentInfo = embedment;
  }

  protected int getBaseStateShift() {
    return BASE_STATE_SHIFT;
  }

  protected boolean isWithinTag() {
    return isWithinTag;
  }

  protected boolean isAttributeEmbedmentToken(@NotNull IElementType tokenType, @NotNull CharSequence attributeName) {
    return myAttributeEmbedmentTokens.contains(tokenType);
  }

  protected boolean isTagEmbedmentStartToken(@NotNull IElementType tokenType, @NotNull CharSequence tagName) {
    return myTagEmbedmentStartTokens.contains(tokenType);
  }

  protected @NotNull List<HtmlEmbeddedContentSupport> getEmbeddedContentSupportList() {
    List<HtmlEmbeddedContentSupport> supports = new ArrayList<>();
    try {
      HtmlEmbeddedContentSupport.Companion.getContentSupports()
        .filter(support -> support.isEnabled(this))
        .forEach(supports::add);
    }
    catch (IllegalArgumentException e) {
      // Tolerate missing extension point for parser tests
    }
    supports.add(new HtmlDefaultEmbeddedContentSupport());
    return supports;
  }

  protected boolean acceptEmbeddedContentProvider(@NotNull HtmlEmbeddedContentProvider provider) {
    return true;
  }

  private static class HtmlLexerPosition implements LexerPosition {

    private final int myOffset;
    private final int myState;
    final LexerPosition myDelegateLexerPosition;
    final Map<HtmlEmbeddedContentProvider, Object> myProvidersState;

    HtmlLexerPosition(int offset, int state, LexerPosition delegateLexerPosition, Map<HtmlEmbeddedContentProvider, Object> providersState) {

      myOffset = offset;
      myState = state;
      myDelegateLexerPosition = delegateLexerPosition;
      myProvidersState = providersState;
    }

    @Override
    public int getOffset() {
      return myOffset;
    }

    @Override
    public int getState() {
      return myState;
    }
  }

  protected boolean isHtmlTagState(int state) {
    return state == _HtmlLexer.START_TAG_NAME || state == _HtmlLexer.END_TAG_NAME;
  }

  /* Deprecated APIs kept for binary compatibility */
  /**
   * This API does no longer work. The value of the field is always {@code false}.
   *
   * @deprecated Use {@link HtmlEmbeddedContentSupport} API
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  protected boolean seenAttribute;

  /**
   * This API does no longer work. The value of the field is always {@code false}.
   *
   * @deprecated Use {@link HtmlEmbeddedContentSupport} API
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  protected boolean seenScript;

  /**
   * This API does no longer work. The value of the field is always {@code false}.
   *
   * @deprecated Use {@link HtmlEmbeddedContentSupport} API
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  protected boolean seenStyle;

  /**
   * This API does no longer work. The value of the field is always {@code false}.
   *
   * @deprecated Use {@link HtmlEmbeddedContentSupport} API
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  protected boolean seenTag;

  /**
   * This API does no longer work. The value of the field is always {@code false}.
   *
   * @deprecated Use {@link HtmlEmbeddedContentSupport} API
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  protected boolean seenStylesheetType;

  /**
   * This API does no longer work. The value of the field is always {@code null}.
   *
   * @deprecated Use {@link HtmlEmbeddedContentSupport} API
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  protected String styleType;

  /**
   * This API does no longer work.
   *
   * @deprecated Use {@link HtmlEmbeddedContentSupport} API
   */
  @SuppressWarnings("unused")
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  protected void registerHandler(IElementType elementType, TokenHandler value) {
    logLegacyLexer();
  }

  /**
   * This API does no longer work.
   *
   * @deprecated Use {@link HtmlEmbeddedContentSupport} API
   */
  @SuppressWarnings("unused")
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  protected HtmlScriptContentProvider findScriptContentProvider(@Nullable String mimeType) {
    logLegacyLexer();
    return null;
  }

  /**
   * This API does no longer work.
   *
   * @deprecated Use {@link HtmlEmbeddedContentSupport} API
   */
  @SuppressWarnings("unused")
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  protected boolean endOfTheEmbeddment(String name) {
    logLegacyLexer();
    return false;
  }

  /**
   * This API does no longer work.
   *
   * @deprecated Use {@link HtmlEmbeddedContentSupport} API
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  @Nullable
  protected Language getStyleLanguage() {
    logLegacyLexer();
    return null;
  }

  /**
   * This API does no longer work.
   *
   * @deprecated Use {@link HtmlEmbeddedContentSupport} API
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  public interface TokenHandler {
    void handleElement(Lexer lexer);
  }

  private static final Set<Class<? extends BaseHtmlLexer>> ourLegacyLexers = ContainerUtil.newConcurrentSet();
  private static final Logger LOG = Logger.getInstance(BaseHtmlLexer.class);

  void logLegacyLexer() {
    if (ourLegacyLexers.add(this.getClass())) {
      LOG.error("Lexer of class " +
                this.getClass().getName() +
                " is using deprecated and no longer working APIs. The lexer might fail to correctly lex source code.");
    }
  }
}
