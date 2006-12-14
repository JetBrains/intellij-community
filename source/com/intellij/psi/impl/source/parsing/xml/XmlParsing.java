/*
 * @author max
 */
package com.intellij.psi.impl.source.parsing.xml;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IChameleonElementType;
import com.intellij.psi.tree.IElementType;
import static com.intellij.psi.xml.XmlElementType.*;
import static com.intellij.psi.xml.XmlTokenType.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Stack;


public class XmlParsing {
  private PsiBuilder myBuilder;
  private Stack<String> myTagNamesStack = new Stack<String>();

  public XmlParsing(final PsiBuilder builder) {
    myBuilder = builder;
  }

  public void parseDocument() {
    final PsiBuilder.Marker document = mark();

    while (token() == XML_COMMENT_START) {
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
      else if (tt == XML_COMMENT_START) {
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
      error.error("Top element is not completed");
      error = null;
    }

    if (rootTagCount == 0) {
      final PsiBuilder.Marker rootTag = mark();
      error = mark();
      error.error("Valid XML document must have a root tag");
      rootTag.done(XML_TAG);
    }

    document.done(XML_DOCUMENT);
  }

  private PsiBuilder.Marker flushError(PsiBuilder.Marker error) {
    if (error != null) {
      error.error("Unexpected tokens");
      error = null;
    }
    return error;
  }

  private void parseDoctype() {
    assert token() == XML_DOCTYPE_START : "Doctype start expected";
    final PsiBuilder.Marker doctype = mark();
    advance();

    while (token() != XML_DOCTYPE_END && !eof()) advance();
    if (eof()) {
      error("Unexpected ent of file");
    }
    else {
      advance();
    }

    doctype.done(XML_DOCTYPE);
  }

  private void parseTag(boolean multipleRootTagError) {
    assert token() == XML_START_TAG_START : "Tag start expected";
    final PsiBuilder.Marker tag = mark();

    if (multipleRootTagError) {
      final PsiBuilder.Marker error = mark();
      advance();
      error.error("Multiple root tags");
    }
    else {
      advance();
    }

    final String tagName;
    if (token() != XML_NAME) {
      error("Tag name expected");
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
      return;
    }

    if (token() == XML_TAG_END) {
      advance();
    }
    else {
      error("Tag start is not closed");
      myTagNamesStack.pop();
      tag.done(XML_TAG);
      return;
    }

    final PsiBuilder.Marker headerDone = mark();
    parseTagContent();

    if (token() == XML_END_TAG_START) {
      final PsiBuilder.Marker footer = mark();
      advance();

      if (token() == XML_NAME) {
        String endName = myBuilder.getTokenText();
        if (!tagName.equals(endName) && myTagNamesStack.contains(endName)) {
          footer.rollbackTo();
          myTagNamesStack.pop();
          tag.doneBefore(XML_TAG, headerDone, "Element " + tagName + " is not closed");
          headerDone.drop();

          // TODO: error tag unclosed?
          return;
        }

        advance();
      }
      footer.drop();

      while (token() != XML_TAG_END && !eof()) {
        error("Unexpected token");
        advance();
      }

      if (token() == XML_TAG_END) advance();
    }
    else {
      error("Unexpected end of file");
    }

    headerDone.drop();
    myTagNamesStack.pop();
    tag.done(XML_TAG);
  }

  private void parseTagContent() {
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
      else if (tt == XML_COMMENT_START) {
        xmlText = terminateText(xmlText);
        parseComment();
      }
      else if (tt == XML_BAD_CHARACTER) {
        xmlText = startText(xmlText);
        final PsiBuilder.Marker error = mark();
        advance();
        error.error("Unescaped & or nonterminated character/entity reference");
      }
      else if (tt instanceof IChameleonElementType) {
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

  @NotNull
  private PsiBuilder.Marker startText(@Nullable PsiBuilder.Marker xmlText) {
    if (xmlText == null) {
      xmlText = mark();
      assert xmlText != null;
    }
    return xmlText;
  }

  private PsiBuilder.Marker mark() {
    return myBuilder.mark();
  }

  @Nullable
  private static PsiBuilder.Marker terminateText(@Nullable PsiBuilder.Marker xmlText) {
    if (xmlText != null) {
      xmlText.done(XML_TEXT);
      xmlText = null;
    }
    return xmlText;
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

  private void parseComment() {
    assert token() == XML_COMMENT_START;
    final PsiBuilder.Marker comment = mark();
    advance();
    while (true) {
      final IElementType tt = token();
      if (tt == XML_COMMENT_CHARACTERS) {
        advance();
        continue;
      }
      else if (tt == XML_BAD_CHARACTER) {
        final PsiBuilder.Marker error = mark();
        advance();
        error.error("Bad character");
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
      error("'=' expected");
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
          error.error("Unescaped & or nonterminated character/entity reference");
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
        error("Attribute value is not closed");
      }
    }
    else {
      error("Attribute value expected");
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
      else if (tt == XML_COMMENT_START) {
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
      error("Processing instruction name expected");
    }
    else {
      advance();
    }

    while (token() == XML_NAME) {
      advance();
      if (token() == XML_EQ) {
        advance();
      }
      else {
        error("'=' expected");
      }
      parseAttributeValue();
    }

    if (token() == XML_PI_END) {
      advance();
    }
    else {
      error("Processing instruction not terminated");
    }

    pi.done(XML_PROCESSING_INSTRUCTION);
  }

  private IElementType token() {
    return myBuilder.getTokenType();
  }

  private boolean eof() {
    return myBuilder.eof();
  }

  private void advance() {
    myBuilder.advanceLexer();
  }

  private void error(final String message) {
    myBuilder.error(message);
  }
}