/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.source.parsing.xml;

import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.ICustomParsingType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.xml.XmlElementType.*;

/*
 * @author max
 */
public class XmlParsing {
  private static final int BALANCING_DEPTH_THRESHOLD = 1000;

  protected final PsiBuilder myBuilder;
  private final Stack<String> myTagNamesStack = new Stack<>();

  public XmlParsing(final PsiBuilder builder) {
    myBuilder = builder;
  }

  public void parseDocument() {
    final PsiBuilder.Marker document = mark();

    while (isCommentToken(token())) {
      parseComment();
    }

    parseProlog();

    int rootTagCount = 0;
    PsiBuilder.Marker error = null;
    while (!eof()) {
      final IElementType tt = token();
      if (tt == XML_START_TAG_START) {
        error = flushError(error);
        rootTagCount++;
        parseTag(rootTagCount > 1);
      }
      else if (isCommentToken(tt)) {
        error = flushError(error);
        parseComment();
      }
      else if (tt == XML_PI_START) {
        error = flushError(error);
        parseProcessingInstruction();
      }
      else if (tt == XML_REAL_WHITE_SPACE) {
        error = flushError(error);
        advance();
      }
      else {
        if (error == null) error = mark();
        advance();
      }
    }

    if (error != null) {
      error.error(XmlErrorMessages.message("top.level.element.is.not.completed"));
    }

    if (rootTagCount == 0) {
      final PsiBuilder.Marker rootTag = mark();
      error = mark();
      error.error(XmlErrorMessages.message("xml.parsing.absent.root.tag"));
      rootTag.done(XML_TAG);
    }

    document.done(XML_DOCUMENT);
  }

  @Nullable
  private static PsiBuilder.Marker flushError(PsiBuilder.Marker error) {
    if (error != null) {
      error.error(XmlErrorMessages.message("xml.parsing.unexpected.tokens"));
    }
    return null;
  }

  private void parseDoctype() {
    assert token() == XML_DOCTYPE_START : "Doctype start expected";
    final PsiBuilder.Marker doctype = mark();
    advance();

    while (token() != XML_DOCTYPE_END && !eof()) advance();
    if (eof()) {
      error(XmlErrorMessages.message("xml.parsing.unexpected.end.of.file"));
    }
    else {
      advance();
    }

    doctype.done(XML_DOCTYPE);
  }

  protected void parseTag(boolean multipleRootTagError) {
    assert token() == XML_START_TAG_START : "Tag start expected";
    final PsiBuilder.Marker tag = mark();

    final String tagName = parseTagHeader(multipleRootTagError, tag);
    if (tagName == null) return;

    final PsiBuilder.Marker content = mark();
    parseTagContent();

    if (token() == XML_END_TAG_START) {
      final PsiBuilder.Marker footer = mark();
      advance();

      if (token() == XML_NAME) {
        String endName = myBuilder.getTokenText();
        if (!tagName.equals(endName) && myTagNamesStack.contains(endName)) {
          footer.rollbackTo();
          myTagNamesStack.pop();
          tag.doneBefore(XML_TAG, content, XmlErrorMessages.message("named.element.is.not.closed", tagName));
          content.drop();
          return;
        }

        advance();
      }
      footer.drop();

      while (token() != XmlTokenType.XML_TAG_END && token() != XmlTokenType.XML_START_TAG_START && token() != XmlTokenType.XML_END_TAG_START && !eof()) {
        error(XmlErrorMessages.message("xml.parsing.unexpected.token"));
        advance();
      }

      if (token() == XML_TAG_END) {
        advance();
      }
      else {
        error(XmlErrorMessages.message("xml.parsing.closing.tag.is.not.done"));
      }
    }
    else {
      error(XmlErrorMessages.message("xml.parsing.unexpected.end.of.file"));
    }

    content.drop();
    myTagNamesStack.pop();
    tag.done(XML_TAG);
  }

  @Nullable
  private String parseTagHeader(final boolean multipleRootTagError, final PsiBuilder.Marker tag) {
    if (multipleRootTagError) {
      final PsiBuilder.Marker error = mark();
      advance();
      error.error(XmlErrorMessages.message("xml.parsing.multiple.root.tags"));
    }
    else {
      advance();
    }

    final String tagName;
    if (token() != XML_NAME || myBuilder.rawLookup(-1) == TokenType.WHITE_SPACE) {
      error(XmlErrorMessages.message("xml.parsing.tag.name.expected"));
      tagName = "";
    }
    else {
      tagName = myBuilder.getTokenText();
      assert tagName != null;
      advance();
    }
    myTagNamesStack.push(tagName);

    do {
      final IElementType tt = token();
      if (tt == XML_NAME) {
        parseAttribute();
      }
      else if (tt == XML_CHAR_ENTITY_REF || tt == XML_ENTITY_REF_TOKEN) {
        parseReference();
      }
      else {
        break;
      }
    }
    while (true);

    if (token() == XML_EMPTY_ELEMENT_END) {
      advance();
      myTagNamesStack.pop();
      tag.done(XML_TAG);
      return null;
    }

    if (token() == XML_TAG_END) {
      advance();
    }
    else {
      error(XmlErrorMessages.message("tag.start.is.not.closed"));
      myTagNamesStack.pop();
      tag.done(XML_TAG);
      return null;
    }

    if (myTagNamesStack.size() > BALANCING_DEPTH_THRESHOLD) {
      error(XmlErrorMessages.message("way.too.unbalanced"));
      tag.done(XML_TAG);
      return null;
    }

    return tagName;
  }

  public void parseTagContent() {
    PsiBuilder.Marker xmlText = null;
    while (token() != XML_END_TAG_START && !eof()) {
      final IElementType tt = token();
      if (tt == XML_START_TAG_START) {
        xmlText = terminateText(xmlText);
        parseTag(false);
      }
      else if (tt == XML_PI_START) {
        xmlText = terminateText(xmlText);
        parseProcessingInstruction();
      }
      else if (tt == XML_ENTITY_REF_TOKEN) {
        xmlText = terminateText(xmlText);
        parseReference();
      }
      else if (tt == XML_CHAR_ENTITY_REF) {
        xmlText = startText(xmlText);
        parseReference();
      }
      else if (tt == XML_CDATA_START) {
        xmlText = startText(xmlText);
        parseCData();
      }
      else if (isCommentToken(tt)) {
        xmlText = terminateText(xmlText);
        parseComment();
      }
      else if (tt == XML_BAD_CHARACTER) {
        xmlText = startText(xmlText);
        final PsiBuilder.Marker error = mark();
        advance();
        error.error(XmlErrorMessages.message("unescaped.ampersand.or.nonterminated.character.entity.reference"));
      }
      else if (tt instanceof ICustomParsingType || tt instanceof ILazyParseableElementType) {
        xmlText = terminateText(xmlText);
        advance();
      }
      else {
        xmlText = startText(xmlText);
        advance();
      }
    }

    terminateText(xmlText);
  }

  protected boolean isCommentToken(final IElementType tt) {
    return tt == XML_COMMENT_START;
  }

  @NotNull
  private PsiBuilder.Marker startText(@Nullable PsiBuilder.Marker xmlText) {
    if (xmlText == null) {
      xmlText = mark();
    }
    return xmlText;
  }

  protected final PsiBuilder.Marker mark() {
    return myBuilder.mark();
  }

  @Nullable
  private static PsiBuilder.Marker terminateText(@Nullable PsiBuilder.Marker xmlText) {
    if (xmlText != null) {
      xmlText.done(XML_TEXT);
    }
    return null;
  }

  private void parseCData() {
    assert token() == XML_CDATA_START;
    final PsiBuilder.Marker cdata = mark();
    while (token() != XML_CDATA_END && !eof()) {
      advance();
    }

    if (!eof()) {
      advance();
    }

    cdata.done(XML_CDATA);
  }

  protected void parseComment() {
    final PsiBuilder.Marker comment = mark();
    advance();
    while (true) {
      final IElementType tt = token();
      if (tt == XML_COMMENT_CHARACTERS|| tt == XML_CONDITIONAL_COMMENT_START
        || tt == XML_CONDITIONAL_COMMENT_START_END || tt == XML_CONDITIONAL_COMMENT_END_START
        || tt == XML_CONDITIONAL_COMMENT_END) {
        advance();
        continue;
      }
      else if (tt == XML_BAD_CHARACTER) {
        final PsiBuilder.Marker error = mark();
        advance();
        error.error(XmlErrorMessages.message("xml.parsing.bad.character"));
        continue;
      }
      if (tt == XML_COMMENT_END) {
        advance();
      }
      break;
    }
    comment.done(XML_COMMENT);
  }

  private void parseReference() {
    if (token() == XML_CHAR_ENTITY_REF) {
      advance();
    }
    else if (token() == XML_ENTITY_REF_TOKEN) {
      final PsiBuilder.Marker ref = mark();
      advance();
      ref.done(XML_ENTITY_REF);
    }
    else {
      assert false : "Unexpected token";
    }
  }

  private void parseAttribute() {
    assert token() == XML_NAME;
    final PsiBuilder.Marker att = mark();
    advance();
    if (token() == XML_EQ) {
      advance();
      parseAttributeValue();
      att.done(XML_ATTRIBUTE);
    }
    else {
      error(XmlErrorMessages.message("expected.attribute.eq.sign"));
      att.done(XML_ATTRIBUTE);
    }
  }

  private void parseAttributeValue() {
    final PsiBuilder.Marker attValue = mark();
    if (token() == XML_ATTRIBUTE_VALUE_START_DELIMITER) {
      while (true) {
        final IElementType tt = token();
        if (tt == null || tt == XML_ATTRIBUTE_VALUE_END_DELIMITER || tt == XML_END_TAG_START || tt == XML_EMPTY_ELEMENT_END ||
            tt == XML_START_TAG_START) {
          break;
        }

        if (tt == XML_BAD_CHARACTER) {
          final PsiBuilder.Marker error = mark();
          advance();
          error.error(XmlErrorMessages.message("unescaped.ampersand.or.nonterminated.character.entity.reference"));
        }
        else if (tt == XML_ENTITY_REF_TOKEN) {
          parseReference();
        }
        else {
          advance();
        }
      }

      if (token() == XML_ATTRIBUTE_VALUE_END_DELIMITER) {
        advance();
      }
      else {
        error(XmlErrorMessages.message("xml.parsing.unclosed.attribute.value"));
      }
    }
    else {
      error(XmlErrorMessages.message("xml.parsing.attribute.value.expected"));
    }

    attValue.done(XML_ATTRIBUTE_VALUE);
  }

  private void parseProlog() {
    final PsiBuilder.Marker prolog = mark();
    while (true) {
      final IElementType tt = token();
      if (tt == XML_PI_START) {
        parseProcessingInstruction();
      }
      else if (tt == XML_DOCTYPE_START) {
        parseDoctype();
      }
      else if (isCommentToken(tt)) {
        parseComment();
      }
      else if (tt == XML_REAL_WHITE_SPACE) {
        advance();
      }
      else {
        break;
      }
    }
    prolog.done(XML_PROLOG);
  }

  private void parseProcessingInstruction() {
    assert token() == XML_PI_START;
    final PsiBuilder.Marker pi = mark();
    advance();
    if (token() != XML_NAME) {
      error(XmlErrorMessages.message("xml.parsing.processing.instruction.name.expected"));
    }
    else {
      advance();
    }

    final IElementType tokenType = token();
    if (tokenType == XML_TAG_CHARACTERS) {
      while (token() == XML_TAG_CHARACTERS) {
        advance();
      }
    } else {
      while (token() == XML_NAME) {
        advance();
        if (token() == XML_EQ) {
          advance();
        }
        else {
          error(XmlErrorMessages.message("expected.attribute.eq.sign"));
        }
        parseAttributeValue();
      }
    }

    if (token() == XML_PI_END) {
      advance();
    }
    else {
      error(XmlErrorMessages.message("xml.parsing.unterminated.processing.instruction"));
    }

    pi.done(XML_PROCESSING_INSTRUCTION);
  }

  @Nullable
  protected final IElementType token() {
    return myBuilder.getTokenType();
  }

  protected final boolean eof() {
    return myBuilder.eof();
  }

  protected final void advance() {
    myBuilder.advanceLexer();
  }

  private void error(final String message) {
    myBuilder.error(message);
  }
}
