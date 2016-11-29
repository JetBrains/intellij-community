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

/*
 * @author max
 */
package com.intellij.lang.html;

import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.CustomParsingType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.containers.Stack;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HtmlParsing {
  @NonNls private static final String TR_TAG = "tr";
  @NonNls private static final String TD_TAG = "td";
  @NonNls private static final String TH_TAG = "th";
  @NonNls private static final String TABLE_TAG = "table";

  private final PsiBuilder myBuilder;
  private final Stack<String> myTagNamesStack = new Stack<>();
  private final Stack<String> myOriginalTagNamesStack = new Stack<>();
  private final Stack<PsiBuilder.Marker> myTagMarkersStack = new Stack<>();
  @NonNls private static final String COMPLETION_NAME = CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED.toLowerCase();

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
    while (!eof()) {
      final IElementType tt = token();
      if (tt == XmlTokenType.XML_START_TAG_START) {
        error = flushError(error);
        parseTag();
        myTagMarkersStack.clear();
        myTagNamesStack.clear();
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
      } else if (tt == XmlTokenType.XML_END_TAG_START) {
        final PsiBuilder.Marker tagEndError = myBuilder.mark();

        advance();
        if (token() == XmlTokenType.XML_NAME) {
          advance();
          if (token() == XmlTokenType.XML_TAG_END) {
            advance();
          }
        }

        tagEndError.error(XmlErrorMessages.message("xml.parsing.closing.tag.matches.nothing"));
      }
      else {
        if (error == null) error = mark();
        advance();
      }
    }

    if (error != null) {
      error.error(XmlErrorMessages.message("top.level.element.is.not.completed"));
    }

    document.done(XmlElementType.HTML_DOCUMENT);
  }

  @Nullable
  private static PsiBuilder.Marker flushError(PsiBuilder.Marker error) {
    if (error != null) {
      error.error(XmlErrorMessages.message("xml.parsing.unexpected.tokens"));
      error = null;
    }
    return error;
  }

  private void parseDoctype() {
    assert token() == XmlTokenType.XML_DOCTYPE_START : "Doctype start expected";
    final PsiBuilder.Marker doctype = mark();
    advance();

    while (token() != XmlTokenType.XML_DOCTYPE_END && !eof()) advance();
    if (eof()) {
      error(XmlErrorMessages.message("xml.parsing.unexpected.end.of.file"));
    }
    else {
      advance();
    }

    doctype.done(XmlElementType.XML_DOCTYPE);
  }

  private void parseTag() {
    assert token() == XmlTokenType.XML_START_TAG_START : "Tag start expected";
    String originalTagName;
    PsiBuilder.Marker xmlText = null;
    while (!eof()) {
      final IElementType tt = token();
      if (tt == XmlTokenType.XML_START_TAG_START) {
        xmlText = terminateText(xmlText);
        final PsiBuilder.Marker tag = mark();

        // Start tag header
        advance();
        if (token() != XmlTokenType.XML_NAME) {
          error(XmlErrorMessages.message("xml.parsing.tag.name.expected"));
          originalTagName = "";
        }
        else {
          originalTagName = myBuilder.getTokenText();
          advance();
        }

        String tagName = StringUtil.toLowerCase(originalTagName);
        while (childTerminatesParentInStack(tagName)) {
          PsiBuilder.Marker top = closeTag();
          top.doneBefore(XmlElementType.HTML_TAG, tag);
        }

        myTagMarkersStack.push(tag);
        myTagNamesStack.push(tagName);
        myOriginalTagNamesStack.push(originalTagName);

        parseHeader(tagName);

        if (token() == XmlTokenType.XML_EMPTY_ELEMENT_END) {
          advance();
          doneTag(tag);
          continue;
        }

        if (token() == XmlTokenType.XML_TAG_END) {
          advance();
        }
        else {
          error(XmlErrorMessages.message("tag.start.is.not.closed"));
          doneTag(tag);
          continue;
        }

        if (HtmlUtil.isSingleHtmlTagL(tagName)) {
          final PsiBuilder.Marker footer = mark();
          if (token() == XmlTokenType.XML_END_TAG_START) {
            advance();
            if (token() == XmlTokenType.XML_NAME) {
              if (tagName.equalsIgnoreCase(myBuilder.getTokenText())) {
                advance();
                footer.drop();
                if (token() == XmlTokenType.XML_TAG_END) {
                  advance();
                }
                doneTag(tag);
                continue;
              }
            }
          }
          footer.rollbackTo();
          doneTag(tag);
        }
      }
      else if (tt == XmlTokenType.XML_PI_START) {
        xmlText = terminateText(xmlText);
        parseProcessingInstruction();
      }
      else if (tt == XmlTokenType.XML_ENTITY_REF_TOKEN) {
        xmlText = terminateText(xmlText);
        parseReference();
      }
      else if (tt == XmlTokenType.XML_CHAR_ENTITY_REF) {
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
        error.error(XmlErrorMessages.message("unescaped.ampersand.or.nonterminated.character.entity.reference"));
      }
      else if (tt instanceof CustomParsingType || tt instanceof ILazyParseableElementType) {
        xmlText = terminateText(xmlText);
        advance();
      }
      else if (token() == XmlTokenType.XML_END_TAG_START) {
        xmlText = terminateText(xmlText);
        final PsiBuilder.Marker footer = mark();
        advance();

        if (token() == XmlTokenType.XML_NAME) {
          String endName = StringUtil.toLowerCase(myBuilder.getTokenText());
          final String parentTagName = !myTagNamesStack.isEmpty() ? myTagNamesStack.peek() : "";
          if (!parentTagName.equals(endName) && !endName.endsWith(COMPLETION_NAME)) {
            final boolean isOptionalTagEnd = HtmlUtil.isOptionalEndForHtmlTagL(parentTagName);
            final boolean hasChancesToMatch = HtmlUtil.isOptionalEndForHtmlTagL(endName) ? childTerminatesParentInStack(endName) : myTagNamesStack.contains(endName);
            if (hasChancesToMatch) {
              footer.rollbackTo();
              if (isOptionalTagEnd) {
                doneTag(myTagMarkersStack.peek());
              }
              else {
                error(XmlErrorMessages.message("named.element.is.not.closed", myOriginalTagNamesStack.peek()));
                doneTag(myTagMarkersStack.peek());
              }
              continue;
            }
            else {
              advance();
              if (token() == XmlTokenType.XML_TAG_END) advance();
              footer.error(XmlErrorMessages.message("xml.parsing.closing.tag.matches.nothing"));
              continue;
            }
          }

          advance();

          while (token() != XmlTokenType.XML_TAG_END && token() != XmlTokenType.XML_START_TAG_START && token() != XmlTokenType.XML_END_TAG_START && !eof()) {
            error(XmlErrorMessages.message("xml.parsing.unexpected.token"));
            advance();
          }
        }
        else {
          error(XmlErrorMessages.message("xml.parsing.closing.tag.name.missing"));
        }
        footer.drop();

        if (token() == XmlTokenType.XML_TAG_END) {
          advance();
        }
        else {
          error(XmlErrorMessages.message("xml.parsing.closing.tag.is.not.done"));
        }
        if (hasTags()) doneTag(myTagMarkersStack.peek());
      } else if ((token() == XmlTokenType.XML_REAL_WHITE_SPACE || token() == XmlTokenType.XML_DATA_CHARACTERS) && !hasTags()) {
        xmlText = terminateText(xmlText);
        advance();
      } else {
        xmlText = startText(xmlText);
        advance();
      }
    }
    terminateText(xmlText);
    while (hasTags()) {
      final String tagName = myTagNamesStack.peek();
      if (!HtmlUtil.isOptionalEndForHtmlTagL(tagName) && !"html".equals(tagName) && !"body".equals(tagName)) {
        error(XmlErrorMessages.message("named.element.is.not.closed", myOriginalTagNamesStack.peek()));
      }
      doneTag(myTagMarkersStack.peek());
    }
  }

  private boolean hasTags() {
    return !myTagNamesStack.isEmpty();
  }

  private PsiBuilder.Marker closeTag() {
    myTagNamesStack.pop();
    myOriginalTagNamesStack.pop();
    return myTagMarkersStack.pop();
  }

  private void doneTag(PsiBuilder.Marker tag) {
    tag.done(XmlElementType.HTML_TAG);
    final String tagName = myTagNamesStack.peek();
    closeTag();

    final String parentTagName = hasTags() ? myTagNamesStack.peek() : "";
    boolean isInlineTagContainer = HtmlUtil.isInlineTagContainerL(parentTagName);
    boolean isOptionalTagEnd = HtmlUtil.isOptionalEndForHtmlTagL(parentTagName);
    if (isInlineTagContainer && HtmlUtil.isHtmlBlockTagL(tagName) && isOptionalTagEnd && !HtmlUtil.isPossiblyInlineTag(tagName)) {
      PsiBuilder.Marker top = closeTag();
      top.doneBefore(XmlElementType.HTML_TAG, tag);
    }
  }

  private void parseHeader(String tagName) {
    boolean freeMakerTag = !tagName.isEmpty() && '#' == tagName.charAt(0);

    do {
      final IElementType tt = token();
      if (freeMakerTag) {
        if (tt == XmlTokenType.XML_EMPTY_ELEMENT_END ||
            tt == XmlTokenType.XML_TAG_END ||
            tt == XmlTokenType.XML_END_TAG_START ||
            tt == XmlTokenType.XML_START_TAG_START) break;
        advance();
      }
      else {
        if (tt == XmlTokenType.XML_NAME) {
          parseAttribute();
        }
        else if (tt == XmlTokenType.XML_CHAR_ENTITY_REF || tt == XmlTokenType.XML_ENTITY_REF_TOKEN) {
          parseReference();
        }
        else {
          break;
        }
      }
    }
    while (!eof());
  }

  private boolean childTerminatesParentInStack(final String childName) {
    boolean isCell = TD_TAG.equals(childName) || TH_TAG.equals(childName);
    boolean isRow = TR_TAG.equals(childName);
    boolean isStructure = isStructure(childName);

    for (int i = myTagNamesStack.size() - 1; i >= 0; i--) {
      String parentName = myTagNamesStack.get(i);
      final boolean isParentTable = TABLE_TAG.equals(parentName);
      final boolean isParentStructure = isStructure(parentName);
      if (isCell && (TR_TAG.equals(parentName) || isParentStructure || isParentTable) ||
          isRow && (isParentStructure || isParentTable) ||
          isStructure && isParentTable) {
        return false;
      }

      if ("li".equals(childName) && ("ul".equals(parentName) || "ol".equals(parentName))) {
        return false;
      }

      if ("dl".equals(parentName) && ("dd".equals(childName) || "dt".equals(childName))) {
        return false;
      }

      if (HtmlUtil.canTerminate(childName, parentName)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isStructure(String childName) {
    return "thead".equals(childName) || "tbody".equals(childName) || "tfoot".equals(childName);
  }

  @NotNull
  private PsiBuilder.Marker startText(@Nullable PsiBuilder.Marker xmlText) {
    if (xmlText == null) {
      xmlText = mark();
      assert xmlText != null;
    }
    return xmlText;
  }

  protected final PsiBuilder.Marker mark() {
    return myBuilder.mark();
  }

  @Nullable
  private static PsiBuilder.Marker terminateText(@Nullable PsiBuilder.Marker xmlText) {
    if (xmlText != null) {
      xmlText.done(XmlElementType.XML_TEXT);
      xmlText = null;
    }
    return xmlText;
  }

  private void parseCData() {
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
        error.error(XmlErrorMessages.message("xml.parsing.bad.character"));
        continue;
      }
      if (tt == XmlTokenType.XML_COMMENT_END) {
        advance();
      }
      break;
    }
    comment.done(XmlElementType.XML_COMMENT);
  }

  private void parseReference() {
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

  private void parseAttribute() {
    assert token() == XmlTokenType.XML_NAME;
    final PsiBuilder.Marker att = mark();
    advance();
    if (token() == XmlTokenType.XML_EQ) {
      advance();
      parseAttributeValue();
      att.done(XmlElementType.XML_ATTRIBUTE);
    }
    else {
      att.done(XmlElementType.XML_ATTRIBUTE);
    }
  }

  private void parseAttributeValue() {
    final PsiBuilder.Marker attValue = mark();
    if (token() == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER) {
      while (true) {
        final IElementType tt = token();
        if (tt == null || tt == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER || tt == XmlTokenType.XML_END_TAG_START || tt == XmlTokenType
          .XML_EMPTY_ELEMENT_END ||
            tt == XmlTokenType.XML_START_TAG_START) {
          break;
        }

        if (tt == XmlTokenType.XML_BAD_CHARACTER) {
          final PsiBuilder.Marker error = mark();
          advance();
          error.error(XmlErrorMessages.message("unescaped.ampersand.or.nonterminated.character.entity.reference"));
        }
        else if (tt == XmlTokenType.XML_ENTITY_REF_TOKEN) {
          parseReference();
        }
        else {
          advance();
        }
      }

      if (token() == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER) {
        advance();
      }
      else {
        error(XmlErrorMessages.message("xml.parsing.unclosed.attribute.value"));
      }
    }
    else {
      if (token() != XmlTokenType.XML_TAG_END && token() != XmlTokenType.XML_EMPTY_ELEMENT_END) {
        advance(); // Single token att value
      }
    }

    attValue.done(XmlElementType.XML_ATTRIBUTE_VALUE);
  }

  private void parseProlog() {
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

  private void parseProcessingInstruction() {
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
        error(XmlErrorMessages.message("expected.attribute.eq.sign"));
      }
      parseAttributeValue();
    }

    if (token() == XmlTokenType.XML_PI_END) {
      advance();
    }
    else {
      error(XmlErrorMessages.message("xml.parsing.unterminated.processing.instruction"));
    }

    pi.done(XmlElementType.XML_PROCESSING_INSTRUCTION);
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

  private void error(final String message) {
    myBuilder.error(message);
  }
}
