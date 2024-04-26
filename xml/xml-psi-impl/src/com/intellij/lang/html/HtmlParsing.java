// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.html;

import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.util.NlsContexts.ParsingError;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.ICustomParsingType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.Processor;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.Stack;
import com.intellij.xml.psi.XmlPsiBundle;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

public class HtmlParsing {
  private final PsiBuilder myBuilder;
  private final Stack<HtmlParserStackItem> myItemsStack = new Stack<>();
  private static final String COMPLETION_NAME = StringUtil.toLowerCase(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED);

  public HtmlParsing(final PsiBuilder builder) {
    myBuilder = builder;
  }

  public void parseDocument() {
    final PsiBuilder.Marker document = mark();

    while (token() == XmlTokenType.XML_COMMENT_START) {
      parseComment();
    }

    parseProlog();

    PsiBuilder.Marker error = null;
    while (shouldContinueMainLoop()) {
      final IElementType tt = token();
      if (tt == XmlTokenType.XML_START_TAG_START) {
        error = flushError(error);
        parseTag();
      }
      else if (tt == XmlTokenType.XML_COMMENT_START) {
        error = flushError(error);
        parseComment();
      }
      else if (tt == XmlTokenType.XML_PI_START) {
        error = flushError(error);
        parseProcessingInstruction();
      }
      else if (tt == XmlTokenType.XML_CHAR_ENTITY_REF || tt == XmlTokenType.XML_ENTITY_REF_TOKEN) {
        parseReference();
      }
      else if (tt == XmlTokenType.XML_REAL_WHITE_SPACE || tt == XmlTokenType.XML_DATA_CHARACTERS) {
        error = flushError(error);
        advance();
      }
      else if (tt == XmlTokenType.XML_END_TAG_START) {
        final PsiBuilder.Marker tagEndError = myBuilder.mark();

        advance();
        if (token() == XmlTokenType.XML_NAME) {
          advance();
          if (token() == XmlTokenType.XML_TAG_END) {
            advance();
          }
        }

        tagEndError.error(XmlPsiBundle.message("xml.parsing.closing.tag.matches.nothing"));
      }
      else if (hasCustomTopLevelContent()) {
        error = parseCustomTopLevelContent(error);
      }
      else {
        if (error == null) error = mark();
        advance();
      }
    }

    flushIncompleteStackItemsWhile((item) -> true);

    if (error != null) {
      error.error(XmlPsiBundle.message("xml.parsing.top.level.element.is.not.completed"));
    }

    document.done(XmlElementType.HTML_DOCUMENT);
  }


  protected final void completeTopStackItem() {
    popItemFromStack().done(myBuilder, null, false);
  }

  protected final void completeTopStackItemBefore(@Nullable PsiBuilder.Marker beforeMarker) {
    popItemFromStack().done(myBuilder, beforeMarker, false);
  }

  protected final void flushIncompleteStackItemsWhile(Predicate<HtmlParserStackItem> itemFilter) {
    flushIncompleteStackItemsWhile(null, itemFilter);
  }

  protected final void flushIncompleteStackItemsWhile(@Nullable PsiBuilder.Marker beforeMarker, Predicate<HtmlParserStackItem> itemFilter) {
    while (!myItemsStack.isEmpty() && itemFilter.test(myItemsStack.peek())) {
      myItemsStack.pop().done(myBuilder, beforeMarker, true);
    }
  }

  protected boolean hasCustomTopLevelContent() {
    return false;
  }

  protected @Nullable PsiBuilder.Marker parseCustomTopLevelContent(@Nullable PsiBuilder.Marker error) {
    return error;
  }

  protected boolean hasCustomTagContent() {
    return false;
  }

  protected @Nullable PsiBuilder.Marker parseCustomTagContent(@Nullable PsiBuilder.Marker xmlText) {
    return xmlText;
  }

  protected boolean hasCustomTagHeaderContent() {
    return false;
  }

  protected void parseCustomTagHeaderContent() {
  }

  protected static @Nullable PsiBuilder.Marker flushError(PsiBuilder.Marker error) {
    if (error != null) {
      error.error(XmlPsiBundle.message("xml.parsing.unexpected.tokens"));
    }
    return null;
  }

  private void parseDoctype() {
    assert token() == XmlTokenType.XML_DOCTYPE_START : "Doctype start expected";
    final PsiBuilder.Marker doctype = mark();
    advance();

    while (token() != XmlTokenType.XML_DOCTYPE_END && !eof()) advance();
    if (eof()) {
      error(XmlPsiBundle.message("xml.parsing.unexpected.end.of.file"));
    }
    else {
      advance();
    }

    doctype.done(XmlElementType.XML_DOCTYPE);
  }

  public void parseTag() {
    assert token() == XmlTokenType.XML_START_TAG_START : "Tag start expected";
    String originalTagName;
    PsiBuilder.Marker xmlText = null;
    while (shouldContinueMainLoop() && shouldContinueParsingTag()) {
      final IElementType tt = token();
      if (tt == XmlTokenType.XML_START_TAG_START) {
        xmlText = terminateText(xmlText);
        final PsiBuilder.Marker tagStart = mark();

        // Start tag header
        advance();
        originalTagName = parseOpenTagName();

        HtmlTagInfo info = createHtmlTagInfo(originalTagName, tagStart);
        while (openingTagAutoClosesTagInStack(info)) {
          completeTopStackItemBefore(tagStart);
        }
        pushItemToStack(info);

        parseTagHeader(info.getNormalizedName());

        if (token() == XmlTokenType.XML_EMPTY_ELEMENT_END) {
          advance();
          doneTag();
          continue;
        }

        if (token() == XmlTokenType.XML_TAG_END) {
          advance();
        }
        else {
          error(XmlPsiBundle.message("xml.parsing.tag.start.is.not.closed"));
          doneTag();
          continue;
        }

        if (isSingleTag(info)) {
          final PsiBuilder.Marker footer = mark();
          while (token() == XmlTokenType.XML_REAL_WHITE_SPACE) {
            advance();
          }
          if (token() == XmlTokenType.XML_END_TAG_START) {
            advance();
            if (token() == XmlTokenType.XML_NAME) {
              if (info.getNormalizedName().equalsIgnoreCase(myBuilder.getTokenText())) {
                advance();
                footer.drop();
                if (token() == XmlTokenType.XML_TAG_END) {
                  advance();
                }
                doneTag();
                continue;
              }
            }
          }
          footer.rollbackTo();
          doneTag();
        }
      }
      else if (tt == XmlTokenType.XML_PI_START) {
        xmlText = terminateText(xmlText);
        parseProcessingInstruction();
      }
      else if (tt == XmlTokenType.XML_ENTITY_REF_TOKEN || tt == XmlTokenType.XML_CHAR_ENTITY_REF) {
        xmlText = startText(xmlText);
        parseReference();
      }
      else if (tt == XmlTokenType.XML_CDATA_START) {
        xmlText = startText(xmlText);
        parseCData();
      }
      else if (tt == XmlTokenType.XML_COMMENT_START) {
        xmlText = startText(xmlText);
        parseComment();
      }
      else if (tt == XmlTokenType.XML_BAD_CHARACTER) {
        xmlText = startText(xmlText);
        final PsiBuilder.Marker error = mark();
        advance();
        error.error(XmlPsiBundle.message("xml.parsing.unescaped.ampersand.or.nonterminated.character.entity.reference"));
      }
      else if (tt instanceof ICustomParsingType || tt instanceof ILazyParseableElementType) {
        xmlText = terminateText(xmlText);
        maybeRemapCurrentToken(tt);
        advance();
      }
      else if (token() == XmlTokenType.XML_END_TAG_START) {
        xmlText = terminateText(xmlText);
        final PsiBuilder.Marker footer = mark();
        advance();

        String endTagName = parseEndTagName();
        if (endTagName != null) {
          endTagName = normalizeTagName(endTagName);
          final var itemOnStack = !myItemsStack.isEmpty() ? myItemsStack.peek() : null;
          if ((itemOnStack instanceof HtmlTagInfo tagOnStack
               && !tagOnStack.getNormalizedName().equals(endTagName)
               && !StringUtil.toLowerCase(endTagName).endsWith(COMPLETION_NAME))
              || !(itemOnStack instanceof HtmlTagInfo)
          ) {
            if (itemOnStack instanceof HtmlTagInfo tagOnStack && isTagFurtherInStack(endTagName)) {
              footer.rollbackTo();
              if (!canClosingTagAutoClose(tagOnStack, endTagName)) {
                error(XmlPsiBundle.message("xml.parsing.named.element.is.not.closed", tagOnStack.getOriginalName()));
              }
              doneTag();
            }
            else {
              if (token() == XmlTokenType.XML_TAG_END) advance();
              footer.error(XmlPsiBundle.message("xml.parsing.closing.tag.matches.nothing"));
            }
            continue;
          }

          while (token() != XmlTokenType.XML_TAG_END &&
                 token() != XmlTokenType.XML_START_TAG_START &&
                 token() != XmlTokenType.XML_END_TAG_START &&
                 !eof()) {
            error(XmlPsiBundle.message("xml.parsing.unexpected.token"));
            advance();
          }
        }
        else {
          error(XmlPsiBundle.message("xml.parsing.closing.tag.name.missing"));
        }
        footer.drop();

        if (token() == XmlTokenType.XML_TAG_END) {
          advance();
        }
        else {
          error(XmlPsiBundle.message("xml.parsing.closing.tag.is.not.done"));
        }

        if (hasTags()) {
          doneTag();
        }
      }
      else if ((token() == XmlTokenType.XML_REAL_WHITE_SPACE || token() == XmlTokenType.XML_DATA_CHARACTERS) && stackSize() == 0) {
        xmlText = terminateText(xmlText);
        advance();
      }
      else if (hasCustomTagContent()) {
        xmlText = parseCustomTagContent(xmlText);
      }
      else {
        xmlText = startText(xmlText);
        advance();
      }
    }
    terminateText(xmlText);
  }

  protected @NotNull HtmlTagInfo createHtmlTagInfo(@NotNull String originalTagName, @NotNull PsiBuilder.Marker startMarker) {
    String normalizedTagName = normalizeTagName(originalTagName);
    return new HtmlTagInfoImpl(normalizedTagName, originalTagName, startMarker);
  }

  protected @NotNull String parseOpenTagName() {
    String originalTagName;
    if (token() != XmlTokenType.XML_NAME) {
      error(XmlPsiBundle.message("xml.parsing.tag.name.expected"));
      originalTagName = "";
    }
    else {
      originalTagName = Objects.requireNonNull(myBuilder.getTokenText());
      advance();
    }
    return originalTagName;
  }

  protected @Nullable String parseEndTagName() {
    String endName;
    if (token() == XmlTokenType.XML_NAME) {
      endName = Objects.requireNonNull(myBuilder.getTokenText());
      advance();
    }
    else {
      endName = null;
    }
    return endName;
  }

  private void parseTagHeader(String tagName) {
    boolean freeMakerTag = !tagName.isEmpty() && '#' == tagName.charAt(0);

    do {
      final IElementType tt = token();
      if (freeMakerTag) {
        if (tt == XmlTokenType.XML_EMPTY_ELEMENT_END ||
            tt == XmlTokenType.XML_TAG_END ||
            tt == XmlTokenType.XML_END_TAG_START ||
            tt == XmlTokenType.XML_START_TAG_START) {
          break;
        }
        advance();
      }
      else {
        if (tt == XmlTokenType.XML_NAME) {
          parseAttribute();
        }
        else if (tt == XmlTokenType.XML_CHAR_ENTITY_REF || tt == XmlTokenType.XML_ENTITY_REF_TOKEN) {
          parseReference();
        }
        else if (hasCustomTagHeaderContent()) {
          parseCustomTagHeaderContent();
        }
        else {
          break;
        }
      }
    }
    while (!eof());
  }

  @ApiStatus.OverrideOnly
  protected boolean shouldContinueMainLoop() {
    return !eof();
  }

  protected boolean shouldContinueParsingTag() {
    return true;
  }


  protected final boolean hasTags() {
    return !myItemsStack.isEmpty() && myItemsStack.peek() instanceof HtmlTagInfo;
  }

  protected final void pushItemToStack(@NotNull HtmlParserStackItem item) {
    myItemsStack.add(item);
  }

  protected final @NotNull HtmlParserStackItem popItemFromStack() {
    return myItemsStack.pop();
  }

  protected String normalizeTagName(@NotNull String originalTagName) {
    return StringUtil.toLowerCase(originalTagName);
  }

  protected final int stackSize() {
    return myItemsStack.size();
  }

  protected final @NotNull HtmlParserStackItem peekStackItem() {
    return myItemsStack.peek();
  }

  protected final @NotNull HtmlTagInfo peekTagInfo() {
    return (HtmlTagInfo)myItemsStack.peek();
  }

  /**
   * Passes stack items starting the top of the stack to the processor.
   * Processing is finished if processor returns false or whole stack has been visited
   */
  protected final void processStackItems(@NotNull Processor<? super HtmlParserStackItem> processor) {
    for (int i = myItemsStack.size() - 1; i >= 0; i--) {
      if (!processor.process(myItemsStack.get(i))) return;
    }
  }

  private boolean isTagFurtherInStack(@NotNull String tagName) {
    for (int i = myItemsStack.size() - 1; i >= 0; i--) {
      var item = myItemsStack.get(i);
      if (item instanceof HtmlTagInfo tagInfo) {
        if (tagInfo.getNormalizedName().equals(tagName)) {
          return true;
        }
      }
      else {
        return false;
      }
    }
    return false;
  }

  protected final void doneTag() {
    if (!(peekStackItem() instanceof HtmlTagInfo)) {
      throw new IllegalStateException(
        "Unexpected item on stack: " + myItemsStack);
    }
    completeTopStackItem();
  }

  protected IElementType getHtmlTagElementType(@NotNull HtmlTagInfo info, int tagLevel) {
    return XmlElementType.HTML_TAG;
  }

  private boolean openingTagAutoClosesTagInStack(final HtmlTagInfo openingTag) {
    var result = new Ref<>(false);
    processStackItems(
      item -> {
        if (item instanceof HtmlTagInfo tagToClose) {
          ThreeState canClose = canOpeningTagAutoClose(tagToClose, openingTag);
          if (canClose != ThreeState.UNSURE) {
            result.set(canClose.toBoolean());
            return false;
          }
        }
        else {
          return false;
        }
        return true;
      }
    );
    return result.get();
  }

  protected boolean isSingleTag(@NotNull HtmlTagInfo tagInfo) {
    return HtmlUtil.isSingleHtmlTag(tagInfo.getNormalizedName(), true);
  }

  protected boolean isEndTagRequired(@NotNull HtmlTagInfo tagInfo) {
    return !HtmlUtil.isTagWithOptionalEnd(tagInfo.getNormalizedName(), true)
           && !"html".equals(tagInfo.getNormalizedName())
           && !"body".equals(tagInfo.getNormalizedName());
  }

  protected @NotNull ThreeState canOpeningTagAutoClose(@NotNull HtmlParsing.HtmlTagInfo tagToClose,
                                                       @NotNull HtmlParsing.HtmlTagInfo openingTag) {
    return HtmlUtil.canOpeningTagAutoClose(tagToClose.getNormalizedName(), openingTag.getNormalizedName(), true);
  }

  protected boolean canClosingTagAutoClose(@NotNull HtmlParsing.HtmlTagInfo tagToClose, @NotNull String closingTag) {
    return HtmlUtil.canClosingTagAutoClose(tagToClose.getNormalizedName(), closingTag, true);
  }

  protected @NotNull PsiBuilder.Marker startText(@Nullable PsiBuilder.Marker xmlText) {
    if (xmlText == null) {
      xmlText = mark();
    }
    return xmlText;
  }

  protected static @Nullable PsiBuilder.Marker terminateText(@Nullable PsiBuilder.Marker xmlText) {
    if (xmlText != null) {
      xmlText.done(XmlElementType.XML_TEXT);
    }
    return null;
  }

  protected void parseCData() {
    assert token() == XmlTokenType.XML_CDATA_START;
    final PsiBuilder.Marker cdata = mark();
    while (token() != XmlTokenType.XML_CDATA_END && !eof()) {
      advance();
    }

    if (!eof()) {
      advance();
    }

    cdata.done(XmlElementType.XML_CDATA);
  }

  protected void parseComment() {
    final PsiBuilder.Marker comment = mark();
    advance();
    while (true) {
      final IElementType tt = token();
      if (tt == XmlTokenType.XML_COMMENT_CHARACTERS || tt == XmlTokenType.XML_CONDITIONAL_COMMENT_START
          || tt == XmlTokenType.XML_CONDITIONAL_COMMENT_START_END || tt == XmlTokenType.XML_CONDITIONAL_COMMENT_END_START
          || tt == XmlTokenType.XML_CONDITIONAL_COMMENT_END) {
        advance();
        continue;
      }
      if (tt == XmlTokenType.XML_ENTITY_REF_TOKEN || tt == XmlTokenType.XML_CHAR_ENTITY_REF) {
        parseReference();
        continue;
      }
      if (tt == XmlTokenType.XML_BAD_CHARACTER) {
        final PsiBuilder.Marker error = mark();
        advance();
        error.error(XmlPsiBundle.message("xml.parsing.bad.character"));
        continue;
      }
      if (tt == XmlTokenType.XML_COMMENT_END) {
        advance();
      }
      break;
    }
    comment.done(XmlElementType.XML_COMMENT);
  }

  protected void parseReference() {
    if (token() == XmlTokenType.XML_CHAR_ENTITY_REF) {
      advance();
    }
    else if (token() == XmlTokenType.XML_ENTITY_REF_TOKEN) {
      final PsiBuilder.Marker ref = mark();
      advance();
      ref.done(XmlElementType.XML_ENTITY_REF);
    }
    else {
      assert false : "Unexpected token";
    }
  }

  protected void parseAttribute() {
    assert token() == XmlTokenType.XML_NAME;
    final PsiBuilder.Marker att = mark();
    advance();
    if (token() == XmlTokenType.XML_EQ) {
      advance();
      parseAttributeValue();
    }
    att.done(getHtmlAttributeElementType());
  }

  protected IElementType getHtmlAttributeElementType() {
    return XmlElementType.XML_ATTRIBUTE;
  }

  protected void parseAttributeValue() {
    final PsiBuilder.Marker attValue = mark();
    if (token() == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER) {
      while (true) {
        final IElementType tt = token();
        if (tt == null
            || tt == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER
            || tt == XmlTokenType.XML_END_TAG_START
            || tt == XmlTokenType.XML_EMPTY_ELEMENT_END
            || tt == XmlTokenType.XML_START_TAG_START) {
          break;
        }

        if (tt == XmlTokenType.XML_BAD_CHARACTER) {
          final PsiBuilder.Marker error = mark();
          advance();
          error.error(XmlPsiBundle.message("xml.parsing.unescaped.ampersand.or.nonterminated.character.entity.reference"));
        }
        else if (tt == XmlTokenType.XML_ENTITY_REF_TOKEN) {
          parseReference();
        }
        else {
          maybeRemapCurrentToken(tt);
          advance();
        }
      }

      if (token() == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER) {
        advance();
      }
      else {
        error(XmlPsiBundle.message("xml.parsing.unclosed.attribute.value"));
      }
    }
    else if (hasCustomAttributeValue()) {
      parseCustomAttributeValue();
    }
    else {
      IElementType tt = token();
      if (tt != XmlTokenType.XML_TAG_END && tt != XmlTokenType.XML_EMPTY_ELEMENT_END) {
        if (tt != null) maybeRemapCurrentToken(tt);
        advance(); // Single token att value
      }
    }

    attValue.done(getHtmlAttributeValueElementType());
  }

  protected IElementType getHtmlAttributeValueElementType() {
    return XmlElementType.XML_ATTRIBUTE_VALUE;
  }

  protected boolean hasCustomAttributeValue() {
    return false;
  }

  protected void parseCustomAttributeValue() {

  }

  protected void parseProlog() {
    while (true) {
      final IElementType tt = token();
      if (tt == XmlTokenType.XML_COMMENT_START) {
        parseComment();
      }
      else if (tt == XmlTokenType.XML_REAL_WHITE_SPACE) {
        advance();
      }
      else {
        break;
      }
    }

    final PsiBuilder.Marker prolog = mark();
    while (true) {
      final IElementType tt = token();
      if (tt == XmlTokenType.XML_PI_START) {
        parseProcessingInstruction();
      }
      else if (tt == XmlTokenType.XML_DOCTYPE_START) {
        parseDoctype();
      }
      else if (tt == XmlTokenType.XML_COMMENT_START) {
        parseComment();
      }
      else if (tt == XmlTokenType.XML_REAL_WHITE_SPACE) {
        advance();
      }
      else {
        break;
      }
    }
    prolog.done(XmlElementType.XML_PROLOG);
  }

  protected void parseProcessingInstruction() {
    assert token() == XmlTokenType.XML_PI_START;
    final PsiBuilder.Marker pi = mark();
    advance();
    if (token() == XmlTokenType.XML_NAME || token() == XmlTokenType.XML_PI_TARGET) {
      advance();
    }

    while (token() == XmlTokenType.XML_NAME) {
      advance();
      if (token() == XmlTokenType.XML_EQ) {
        advance();
      }
      else {
        error(XmlPsiBundle.message("xml.parsing.expected.attribute.eq.sign"));
      }
      parseAttributeValue();
    }

    if (token() == XmlTokenType.XML_PI_END) {
      advance();
    }
    else {
      error(XmlPsiBundle.message("xml.parsing.unterminated.processing.instruction"));
    }

    pi.done(XmlElementType.XML_PROCESSING_INSTRUCTION);
  }

  protected final PsiBuilder getBuilder() {
    return myBuilder;
  }

  protected final IElementType token() {
    return myBuilder.getTokenType();
  }

  protected final boolean eof() {
    return myBuilder.eof();
  }

  protected final void advance() {
    myBuilder.advanceLexer();
  }

  protected final PsiBuilder.Marker mark() {
    return myBuilder.mark();
  }

  protected void error(@NotNull @ParsingError String message) {
    myBuilder.error(message);
  }

  /**
   * Allows overriding tokens returned by the lexer in certain places.
   * <p>
   * Implementations should conditionally call {@code builder.remapCurrentToken()}.
   */
  @ApiStatus.Experimental
  protected void maybeRemapCurrentToken(@NotNull IElementType tokenType) {
  }

  public interface HtmlParserStackItem {
    /**
     * Make all of associated with the item markers dropped or done.
     *
     * @param builder current PsiBuilder
     * @param beforeMarker an optional marker before, which the item should be done
     * @param incomplete whether the item is missing the closing tag, token, etc.
     */
    void done(@NotNull PsiBuilder builder, @Nullable PsiBuilder.Marker beforeMarker, boolean incomplete);
  }

  public interface HtmlTagInfo extends HtmlParserStackItem {
    @NotNull String getNormalizedName();

    @NotNull String getOriginalName();
  }

  protected class HtmlTagInfoImpl implements HtmlTagInfo {
    private final @NotNull String normalizedName;
    private final @NotNull String originalName;
    private final @NotNull PsiBuilder.Marker startMarker;

    protected HtmlTagInfoImpl(
      @NotNull String normalizedName,
      @NotNull String originalName,
      @NotNull PsiBuilder.Marker marker
    ) {
      this.normalizedName = normalizedName;
      this.originalName = originalName;
      startMarker = marker;
    }

    @Override
    public @NotNull String getNormalizedName() {
      return normalizedName;
    }

    @Override
    public @NotNull String getOriginalName() {
      return originalName;
    }

    @Override
    public void done(@NotNull PsiBuilder builder, @Nullable PsiBuilder.Marker beforeMarker, boolean incomplete) {
      var myElementType = getHtmlTagElementType(this, myItemsStack.size() + 1);
      if (beforeMarker == null) {
        if (incomplete && isEndTagRequired(this)) {
          builder.error(XmlPsiBundle.message("xml.parsing.named.element.is.not.closed", getOriginalName()));
        }
        startMarker.done(myElementType);
      } else {
        if (incomplete && isEndTagRequired(this)) {
          beforeMarker.precede()
            .errorBefore(XmlPsiBundle.message("xml.parsing.named.element.is.not.closed", getOriginalName()), beforeMarker);
        }
        startMarker.doneBefore(myElementType, beforeMarker);
      }
    }
  }
}
