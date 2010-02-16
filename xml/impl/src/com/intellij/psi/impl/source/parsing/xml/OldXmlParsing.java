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
import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lexer.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.ParsingContext;
import com.intellij.psi.impl.source.parsing.TokenProcessor;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.CharTable;
import com.intellij.xml.XmlBundle;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Mike
 */
public class OldXmlParsing implements XmlElementType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.xml.XmlParser");

  public static final TokenSet XML_WHITE_SPACE_OR_COMMENT_BIT_SET =
    TokenSet.create(new IElementType[]{XML_WHITE_SPACE, XML_COMMENT_START, XML_COMMENT_CHARACTERS,
                                                                                        XML_COMMENT_END, XML_BAD_CHARACTER});

  public static final TokenSet XML_COMMENT_BIT_SET =
    TokenSet.create(new IElementType[]{XML_COMMENT_START, XML_COMMENT_CHARACTERS, XML_COMMENT_END});

  private final ParsingContext myContext;
  private int myLastTokenEnd = -1;

  public OldXmlParsing(ParsingContext context) {
    myContext = context;
  }


  public TreeElement parse(Lexer originalLexer, CharSequence buffer, int startOffset, int endOffset, PsiManager manager) {
    final Lexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(XML_WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(buffer, startOffset, endOffset);

    final FileElement dummyRoot = DummyHolderFactory.createHolder(manager, null, myContext.getCharTable()).getTreeElement();

    CompositeElement root = ASTFactory.composite(XML_DOCUMENT);
    dummyRoot.rawAddChildren(root);
    root.rawAddChildren(parseProlog(lexer));
    parseGenericXml(lexer, root, new HashSet<String>());

    insertMissingTokens(root,
                                  originalLexer,
                                  startOffset,
                                  endOffset,
                                  -1, WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return root;
  }

  public void parseGenericXml(Lexer lexer, CompositeElement root, Set<String> names) {
    boolean rootTagChecked = false;
    IElementType tokenType;

    while ((tokenType = lexer.getTokenType()) != null) {
      if (tokenType == XML_ATTLIST_DECL_START) {
        root.rawAddChildren(parseAttlistDecl(lexer));
      }
      else if (tokenType == XML_ELEMENT_DECL_START) {
        root.rawAddChildren(parseElementDecl(lexer));
      }
      else if (tokenType == XML_ENTITY_DECL_START) {
        root.rawAddChildren(parseEntityDecl(lexer));
      }
      else if (tokenType == XML_NOTATION_DECL_START) {
        root.rawAddChildren(parseNotationDecl(lexer));
      }
      else if (tokenType == XML_ENTITY_REF_TOKEN) {
        root.rawAddChildren(parseEntityRef(lexer));
      }
      else if (parseProcessingInstruction(root, lexer)) {
      }
      else if (_parseTag(root, lexer, names)) {
      }
      else if (parseConditionalSection(root, lexer)) {
      }
      else if (tokenType != null) {
        if (!rootTagChecked) {
          //checkRootTag(root);
          rootTagChecked = true;
        }

        addToken(root, lexer);
      }
    }

    if (!rootTagChecked) {
      //checkRootTag(root);
      rootTagChecked = true;
    }

    insertMissingTokens(root, ((FilterLexer)lexer).getOriginal(),
                                  0,
                                  lexer.getBufferEnd(),
                                  -1, WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
  }

  public TreeElement parseNotationDecl(Lexer lexer) {
    CompositeElement decl = ASTFactory.composite(XML_NOTATION_DECL);

    if (lexer.getTokenType() != XML_NOTATION_DECL_START) {
      return decl;
    }

    addToken(decl, lexer);

    if (!parseName(decl, lexer)) {
      return decl;
    }

    parseEntityDeclContent(decl, lexer);

    if (lexer.getTokenType() != null) {
      addToken(decl, lexer);
    }

    return decl;
  }

  private TreeElement parseEntityDecl(Lexer lexer) {
    CompositeElement decl = ASTFactory.composite(XML_ENTITY_DECL);

    if (lexer.getTokenType() != XML_ENTITY_DECL_START) {
      return decl;
    }

    addToken(decl, lexer);

    if (lexer.getTokenType() == XML_PERCENT) {
      addToken(decl, lexer);
    }

    if (parseCompositeName(lexer, decl)) return decl;

    parseEntityDeclContent(decl, lexer);

    if (lexer.getTokenType() != null) {
      addToken(decl, lexer);
    }

    return decl;
  }

  private boolean parseCompositeName(final Lexer lexer, final CompositeElement decl) {
    if (!parseName(decl, lexer)) {
      if (lexer.getTokenType() == XML_LEFT_PAREN) {
        parseGroup(decl, lexer);
      } else {
        decl.rawAddChildren(Factory.createErrorElement(XmlBundle.message("dtd.parser.message.name.expected")));
        return true;
      }
    }
    return false;
  }

  public void parseEntityDeclContent(CompositeElement decl, Lexer lexer) {
    IElementType tokenType = lexer.getTokenType();
    if (tokenType != XML_ATTRIBUTE_VALUE_START_DELIMITER &&
        tokenType != XML_DOCTYPE_PUBLIC &&
        tokenType != XML_DOCTYPE_SYSTEM) {
      decl.rawAddChildren(Factory.createErrorElement(XmlBundle.message("dtd.parser.message.literal.public.system.expected")));
      return;
    }

    while (tokenType != XML_TAG_END && tokenType != null) {
      if (tokenType == XML_ATTRIBUTE_VALUE_START_DELIMITER) {
        parseAttributeValue(decl, lexer);
      }
      else {
        addToken(decl, lexer);
      }

      tokenType = lexer.getTokenType();
    }
  }

  private boolean parseConditionalSection(CompositeElement parent, Lexer lexer) {
    if (lexer.getTokenType() != XML_CONDITIONAL_SECTION_START) {
      return false;
    }
    CompositeElement conditionalSection = ASTFactory.composite(XML_CONDITIONAL_SECTION);
    parent.rawAddChildren(conditionalSection);

    addToken(conditionalSection, lexer);
    IElementType tokenType = lexer.getTokenType();
    if (tokenType != XML_CONDITIONAL_IGNORE &&
        tokenType != XML_CONDITIONAL_INCLUDE &&
        tokenType != XML_ENTITY_REF_TOKEN) {
      return true;
    }

    if (tokenType == XML_ENTITY_REF_TOKEN) {
      conditionalSection.rawAddChildren(parseEntityRef(lexer) );
    } else {
      addToken(conditionalSection, lexer);
    }

    if (lexer.getTokenType() != XML_MARKUP_START) {
      return true;
    }

    parseMarkupContent(lexer, conditionalSection);

    if (lexer.getTokenType() != XML_CONDITIONAL_SECTION_END) {
      return true;
    }
    addToken(conditionalSection, lexer);
    return true;
  }

  private boolean parseProcessingInstruction(CompositeElement parent, Lexer lexer) {
    if (lexer.getTokenType() != XML_PI_START) {
      return false;
    }
    CompositeElement tag = ASTFactory.composite(XML_PROCESSING_INSTRUCTION);
    parent.rawAddChildren(tag);

    addToken(tag, lexer);
    if (lexer.getTokenType() != XML_PI_TARGET) {
      return true;
    }
    addToken(tag, lexer);
    if (lexer.getTokenType() != XML_PI_END) {
      return true;
    }
    addToken(tag, lexer);
    return true;
  }

  private boolean _parseTag(CompositeElement parent, Lexer lexer, Set<String> names) {
    if (lexer.getTokenType() != XML_START_TAG_START) {
      return false;
    }

    CompositeElement tag = ASTFactory.composite(XML_TAG);
    parent.rawAddChildren(tag);

    addToken(tag, lexer);

    if (lexer.getTokenType() != XML_TAG_NAME) {
      return true;
    }

    final String openedName = lexer.getTokenText();
    addToken(tag, lexer);

    parseAttributeList(tag, lexer);

    while (lexer.getTokenType() == XML_BAD_CHARACTER || lexer.getTokenType() == XML_NAME) {
      addToken(tag, lexer);
      parseAttributeList(tag, lexer);
    }
    TreeElement tagEnd;
    if (lexer.getTokenType() == XML_TAG_END) {
      tagEnd = addToken(tag, lexer);
      boolean setFlag = false;
      if (!names.contains(openedName)) {
        names.add(openedName);
        setFlag = true;
      }
      
      CompositeElement text = null;
      while (true) {
        if (parseProcessingInstruction(tag, lexer)) {
        }
        else if (_parseTag(tag, lexer, names)) {
        }
        else if (lexer.getTokenType() == XML_DATA_CHARACTERS) {
          if (text == null) {
            text = ASTFactory.composite(XML_TEXT);
            tag.rawAddChildren(text);
          }
          addToken(text, lexer);
        }
        else if (lexer.getTokenType() == XML_CDATA_START) {
          addToken(tag, lexer);
        }
        else if (lexer.getTokenType() == XML_CDATA_END) {
          addToken(tag, lexer);
        }
        else if (lexer.getTokenType() == XML_CHAR_ENTITY_REF) {
          addToken(tag, lexer);
        }
        else if (lexer.getTokenType() == XML_ENTITY_REF_TOKEN) {
          tag.rawAddChildren(parseEntityRef(lexer));
        }
        else {
          break;
        }
      }
      if (setFlag) {
        names.remove(openedName);
      }

      final int pos = lexer.getTokenStart();

      if (lexer.getTokenType() != XML_END_TAG_START) {
        tagEnd.rawInsertAfterMe(Factory.createErrorElement(XmlErrorMessages.message("element.is.not.closed")));

        return false;
      }
      TreeElement endTagStart = createTokenElement(lexer, myContext.getCharTable());
      lexer.advance();

      if (lexer.getTokenType() != XML_TAG_NAME) {
        tag.rawAddChildren(endTagStart);
        return true;
      }

      final String closingName = lexer.getBufferSequence().subSequence(lexer.getTokenStart(),
                                                                         lexer.getTokenEnd()).toString();

      if (!closingName.equals(openedName) && names.contains(closingName)) {
        lexer.start(lexer.getBufferSequence(), pos, lexer.getBufferEnd());
        if (tagEnd != null) {
          final TreeElement start = tagEnd.getTreeNext();
          tagEnd.setTreeNext(null);
          if (start != null) {
            parent.rawAddChildren(start);
          }
        }
        tagEnd.rawInsertAfterMe(Factory.createErrorElement(XmlErrorMessages.message("element.is.not.closed")));
        return true;
      }

      tag.rawAddChildren(endTagStart);
      addToken(tag, lexer);

      if (lexer.getTokenType() != XML_TAG_END) {
        return true;
      }

      addToken(tag, lexer);
    }
    else if (lexer.getTokenType() == XML_EMPTY_ELEMENT_END) {
      addToken(tag, lexer);
    }
    else {
      tag.getLastChildNode().rawInsertAfterMe(Factory.createErrorElement(XmlErrorMessages.message("element.is.not.closed")));
    }

    return true;
  }

  public static TreeElement createTokenElement(Lexer lexer, CharTable table) {
    IElementType tokenType = lexer.getTokenType();
    if (tokenType == null) return null;
    return ASTFactory.leaf(tokenType, LexerUtil.internToken(lexer, table));
  }


  private TreeElement parseEntityRef(Lexer lexer) {
    CompositeElement ref = ASTFactory.composite(XML_ENTITY_REF);

    if (lexer.getTokenType() == XML_ENTITY_REF_TOKEN) {
      addToken(ref, lexer);
    }

    return ref;
  }

  private TreeElement parseProlog(Lexer lexer) {
    CompositeElement prolog = ASTFactory.composite(XML_PROLOG);

    while (parseProcessingInstruction(prolog, lexer)) {
    }

    if (lexer.getTokenType() == XML_DECL_START) {
      prolog.rawAddChildren(parseDecl(lexer));
    }

    while (parseProcessingInstruction(prolog, lexer)) {
    }

    if (lexer.getTokenType() == XML_DOCTYPE_START) {
      prolog.rawAddChildren(parseDocType(lexer));
    }

    while (parseProcessingInstruction(prolog, lexer)) {
    }

    return prolog;
  }

  private TreeElement parseDocType(Lexer lexer) {
    CompositeElement docType = ASTFactory.composite(XML_DOCTYPE);

    if (lexer.getTokenType() != XML_DOCTYPE_START) {
      return docType;
    }

    addToken(docType, lexer);

    if (lexer.getTokenType() != XML_NAME) {
      return docType;
    }

    addToken(docType, lexer);

    if (lexer.getTokenType() == XML_DOCTYPE_SYSTEM) {
      addToken(docType, lexer);

      if (lexer.getTokenType() == XML_ATTRIBUTE_VALUE_TOKEN) {
        addToken(docType, lexer);
      }
    }
    else if (lexer.getTokenType() == XML_DOCTYPE_PUBLIC) {
      addToken(docType, lexer);

      if (lexer.getTokenType() == XML_ATTRIBUTE_VALUE_TOKEN) {
        addToken(docType, lexer);
      }

      if (lexer.getTokenType() == XML_ATTRIBUTE_VALUE_TOKEN) {
        addToken(docType, lexer);
      }
    }

    if (lexer.getTokenType() == XML_MARKUP_START) {
      docType.rawAddChildren(parseMarkupDecl(lexer));
    }

    if (lexer.getTokenType() != XML_DOCTYPE_END) {
      return docType;
    }

    addToken(docType, lexer);

    return docType;
  }

  private TreeElement parseMarkupDecl(Lexer lexer) {
    CompositeElement decl = ASTFactory.composite(XML_MARKUP_DECL);

    parseMarkupContent(lexer, decl);

    return decl;
  }

  private void parseMarkupContent(Lexer lexer, CompositeElement decl) {
    IElementType tokenType = lexer.getTokenType();
    if (tokenType == XML_MARKUP_START) {
      addToken(decl, lexer);
    }

    while (true) {
      tokenType = lexer.getTokenType();
      
      if (tokenType == XML_ELEMENT_DECL_START) {
        decl.rawAddChildren(parseElementDecl(lexer));
      }
      else if (tokenType == XML_ATTLIST_DECL_START) {
        decl.rawAddChildren(parseAttlistDecl(lexer));
      }
      else if (tokenType == XML_ENTITY_DECL_START) {
        decl.rawAddChildren(parseEntityDecl(lexer));
      }
      else if (tokenType == XML_NOTATION_DECL_START) {
        decl.rawAddChildren(parseNotationDecl(lexer));
      } else if (tokenType == XML_ENTITY_REF_TOKEN) {
        decl.rawAddChildren(parseEntityRef(lexer));
      }
      else if (parseConditionalSection(decl, lexer)) {
      }
      else {
        break;
      }
    }

    if (tokenType == XML_MARKUP_END) {
      addToken(decl, lexer);
    }
  }

  private TreeElement parseElementDecl(Lexer lexer) {
    CompositeElement decl = ASTFactory.composite(XML_ELEMENT_DECL);

    if (lexer.getTokenType() != XML_ELEMENT_DECL_START) {
      return decl;
    }

    addToken(decl, lexer);

    if (parseCompositeName(lexer, decl)) return decl;

    doParseContentSpec(decl, lexer, false);

    if (lexer.getTokenType() == XML_TAG_END) {
      addToken(decl, lexer);
    }

    return decl;
  }

  private boolean parseName(CompositeElement decl, Lexer lexer) {
    if (lexer.getTokenType() == XML_NAME) {
      addToken(decl, lexer);

      return true;
    }

    if (lexer.getTokenType() == XML_ENTITY_REF_TOKEN) {
      decl.rawAddChildren(parseEntityRef(lexer));
      return true;
    }

    return false;
  }

  public ASTNode parseElementContentSpec(CompositeElement parent, Lexer lexer) {
    return doParseContentSpec(parent, lexer, true);
  }

  private ASTNode doParseContentSpec(final CompositeElement parent, final Lexer lexer, boolean topLevel) {
    if (myLastTokenEnd == lexer.getTokenStart()) {
      parent.rawAddChildren(Factory.createErrorElement(XmlBundle.message("dtd.parser.message.whitespace.expected")));
    } else if (!topLevel) {
      final IElementType tokenType = lexer.getTokenType();
      String tokenText;

      if (tokenType != XML_LEFT_PAREN &&
          tokenType != XML_ENTITY_REF_TOKEN &&
          tokenType != XML_CONTENT_ANY &&
          tokenType != XML_CONTENT_EMPTY &&
          (tokenType != XML_NAME || ( !(tokenText = TreeUtil.getTokenText(lexer)).equals("-") && !tokenText.equals("O"))) // sgml compatibility
        ) {
        parent.rawAddChildren(Factory.createErrorElement(XmlBundle.message("dtd.parser.message.left.paren.or.entityref.or.empty.or.any.expected")));
      }
    }

    CompositeElement spec = ASTFactory.composite(XML_ELEMENT_CONTENT_SPEC);
    parent.rawAddChildren(spec);

    parseElementContentSpecInner(lexer, spec, topLevel);

    return spec;
  }

  private boolean parseElementContentSpecInner(final Lexer lexer, final CompositeElement spec, boolean topLevel) {
    IElementType tokenType = lexer.getTokenType();
    boolean endedWithDelimiter = false;

    while (
      tokenType != null &&
      tokenType != XML_TAG_END &&
      tokenType != XML_START_TAG_START &&
      tokenType != XML_ELEMENT_DECL_START &&
      tokenType != XML_RIGHT_PAREN
    ) {
      if (tokenType == XML_BAR && topLevel) {
        addToken(spec, lexer);
        tokenType = lexer.getTokenType();
        continue;
      } else
      if (tokenType == XML_LEFT_PAREN) {
        if (!parseGroup(spec, lexer)) return false;
        endedWithDelimiter = false;
      } else
      if (tokenType == XML_ENTITY_REF_TOKEN) {
        spec.rawAddChildren(parseEntityRef(lexer));
        endedWithDelimiter = false;
      } else if (tokenType == XML_NAME ||
                 tokenType == XML_CONTENT_EMPTY ||
                 tokenType == XML_CONTENT_ANY ||
                 tokenType == XML_PCDATA
                 ) {
        addToken(spec, lexer);
        endedWithDelimiter = false;
      }
      else {
        spec.rawAddChildren(Factory.createErrorElement(XmlBundle.message("dtd.parser.message.name.or.entity.ref.expected")));
        return false;
      }

      tokenType = lexer.getTokenType();

      if (tokenType == XML_STAR ||
          tokenType == XML_PLUS ||
          tokenType == XML_QUESTION
         ) {
        addToken(spec, lexer);
        tokenType = lexer.getTokenType();

        if (tokenType == XML_PLUS) {
          addToken(spec, lexer);
          tokenType = lexer.getTokenType();
        }
      }
      if (tokenType == XML_BAR || tokenType == XML_COMMA) {
        addToken(spec, lexer);
        tokenType = lexer.getTokenType();
        endedWithDelimiter = true;
      }
    }

    if (endedWithDelimiter && tokenType == XML_RIGHT_PAREN) {
      spec.rawAddChildren(Factory.createErrorElement(XmlBundle.message("dtd.parser.message.name.or.entity.ref.expected")));
    }
    return true;
  }

  private boolean parseGroup(final CompositeElement spec, final Lexer lexer) {
    addToken(spec, lexer);
    boolean b = parseElementContentSpecInner(lexer, spec, false);
    if (b && lexer.getTokenType() == XML_RIGHT_PAREN) {
      addToken(spec, lexer);
      return true;
    } else if (b) {
      spec.rawAddChildren(Factory.createErrorElement(XmlBundle.message("dtd.parser.message.rbrace.expected")));
      return false;
    }
    return b;
  }

  private TreeElement parseAttlistDecl(Lexer lexer) {
    CompositeElement decl = ASTFactory.composite(XML_ATTLIST_DECL);

    if (lexer.getTokenType() != XML_ATTLIST_DECL_START) {
      return decl;
    }

    addToken(decl, lexer);

    if (!parseName(decl, lexer)) {
      final IElementType tokenType = lexer.getTokenType();
      if (tokenType == XML_LEFT_PAREN) {
        parseGroup(decl, lexer);
      } else {
        decl.rawAddChildren(Factory.createErrorElement(XmlBundle.message("dtd.parser.message.name.expected")));
        return decl;
      }
    }

    parseAttlistContent(decl, lexer);

    if (lexer.getTokenType() == XML_TAG_END) {
      addToken(decl, lexer);
    }

    return decl;
  }

  public void parseAttlistContent(CompositeElement parent, Lexer lexer) {
    while (true) {
      if (lexer.getTokenType() == XML_ENTITY_REF_TOKEN) {
        parent.rawAddChildren(parseEntityRef(lexer));
      }
      else if (parseAttributeDecl(parent, lexer)) {
      }
      else {
        break;
      }
    }
  }

  private boolean parseAttributeDecl(CompositeElement parent, Lexer lexer) {
    if (lexer.getTokenType() != XML_NAME) {
      return false;
    }

    CompositeElement decl = ASTFactory.composite(XML_ATTRIBUTE_DECL);
    parent.rawAddChildren(decl);

    addToken(decl, lexer);

    return parseAttributeContentSpec(decl, lexer);
  }

  public boolean parseAttributeContentSpec(CompositeElement parent, Lexer lexer) {
    if (parseName(parent, lexer)) {
    }
    else if (lexer.getTokenType() == XML_LEFT_PAREN) {
      parent.rawAddChildren(parseEnumeratedType(lexer));
    }
    else {
      return true;
    }

    if (lexer.getTokenType() == XML_ATT_IMPLIED) {
      addToken(parent, lexer);
    }
    else if (lexer.getTokenType() == XML_ATT_REQUIRED) {
      addToken(parent, lexer);
    }
    else if (lexer.getTokenType() == XML_ATT_FIXED) {
      addToken(parent, lexer);

      if (lexer.getTokenType() == XML_ATTRIBUTE_VALUE_START_DELIMITER) {
        parseAttributeValue(parent, lexer);
      }
    }
    else if (lexer.getTokenType() == XML_ATTRIBUTE_VALUE_START_DELIMITER) {
      parseAttributeValue(parent, lexer);
    }

    return true;
  }

  private CompositeElement parseEnumeratedType(Lexer lexer) {
    CompositeElement enumeratedType = ASTFactory.composite(XML_ENUMERATED_TYPE);
    addToken(enumeratedType, lexer);

    parseEnumeratedTypeContent(enumeratedType, lexer);

    if (lexer.getTokenType() == XML_RIGHT_PAREN) {
      addToken(enumeratedType, lexer);
    }

    return enumeratedType;
  }

  public void parseEnumeratedTypeContent(CompositeElement enumeratedType, Lexer lexer) {
    while (true) {
      if (lexer.getTokenType() == XML_ENTITY_REF_TOKEN) {
        enumeratedType.rawAddChildren(parseEntityRef(lexer));
      continue;
      }

      if (lexer.getTokenType() != XML_NAME && lexer.getTokenType() != XML_BAR) break;
      addToken(enumeratedType, lexer);
    }
  }

  TreeElement parseDecl(Lexer lexer) {
    CompositeElement decl = ASTFactory.composite(XML_DECL);

    if (lexer.getTokenType() != XML_DECL_START) {
      return decl;
    }
    addToken(decl, lexer);

    parseAttributeList(decl, lexer);

    if (lexer.getTokenType() == XML_DECL_END) {
      addToken(decl, lexer);
    }
    else {
      decl.rawAddChildren(Factory.createErrorElement(XmlErrorMessages.message("expected.prologue.tag.termination.expected")));
    }

    return decl;
  }

  private void parseAttributeList(CompositeElement tag, Lexer lexer) {
    CompositeElement parent = tag;
    int lastPosition = -1;
    while (true) {
      if (lexer.getTokenType() == XML_ENTITY_REF_TOKEN) {
        tag.rawAddChildren(parseEntityRef(lexer));
      continue;
      }

      if (lexer.getTokenType() != XML_NAME) {
        return;
      }

      if (lastPosition != -1) {
        if (lastPosition == lexer.getTokenStart()) {
          tag.rawAddChildren(Factory.createErrorElement(XmlErrorMessages.message("expected.whitespace")));
          lastPosition = -1;
        }
      }

      if (tag instanceof XmlTag) {
        CompositeElement attribute = ASTFactory.composite(XML_ATTRIBUTE);
        tag.rawAddChildren(attribute);
        parent = attribute;
      }
      
      addToken(parent, lexer);

      if (lexer.getTokenType() != XML_EQ) {
        parent.rawAddChildren(Factory.createErrorElement(XmlErrorMessages.message("expected.attribute.eq.sign")));
        continue;
      }

      addToken(parent, lexer);
      
      if (tag instanceof XmlTag) {
        CompositeElement attributeValue = ASTFactory.composite(XML_ATTRIBUTE_VALUE);
        parent.rawAddChildren(attributeValue);
        parent = attributeValue;
      }

      if (lexer.getTokenType() != XML_ATTRIBUTE_VALUE_START_DELIMITER) {
        return;
      }

      addToken(parent, lexer);

      if (lexer.getTokenType() == XML_ATTRIBUTE_VALUE_TOKEN) {
        addToken(parent, lexer);
        
        if (lexer.getTokenType() == XML_ATTRIBUTE_VALUE_END_DELIMITER) {
          lastPosition = lexer.getTokenEnd();
          addToken(parent, lexer);
        }
        else {
          lastPosition = -1;
        }
      }
      else if (lexer.getTokenType() == XML_ATTRIBUTE_VALUE_END_DELIMITER) {
        lastPosition = lexer.getTokenEnd();
        addToken(parent, lexer);
      }
      else {
        lastPosition = -1;
      }
      
      if (tag instanceof XmlTag) {
        parent = tag;
      }
    }
  }

  private int parseAttributeValue(CompositeElement parent, Lexer lexer) {
    if (lexer.getTokenType() != XML_ATTRIBUTE_VALUE_START_DELIMITER) {
      return -1;
    }

    CompositeElement value = ASTFactory.composite(XML_ATTRIBUTE_VALUE);

    parent.rawAddChildren(value);

    addToken(value, lexer);

    while (true) {
      if (lexer.getTokenType() == XML_ATTRIBUTE_VALUE_TOKEN) {
        addToken(value, lexer);
      }
      else if (lexer.getTokenType() == XML_CHAR_ENTITY_REF) {
        addToken(value, lexer);
      }
      else if (lexer.getTokenType() == XML_ENTITY_REF_TOKEN) {
        value.rawAddChildren(parseEntityRef(lexer));
      }
      else {
        break;
      }
    }

    if (lexer.getTokenType() != XML_ATTRIBUTE_VALUE_END_DELIMITER) {
      return -1;
    }

    int tokenEnd = lexer.getTokenEnd();
    addToken(value, lexer);
    return tokenEnd;
  }

  private TreeElement addToken(CompositeElement decl, Lexer lexer) {
    final TreeElement element = createTokenElement(lexer, myContext.getCharTable());
    if (element != null) {
      decl.rawAddChildren(element);
      myLastTokenEnd = lexer.getTokenEnd();
      lexer.advance();
    }
    return element;
  }

  public TreeElement parseMarkupDecl(CharSequence text) {
    CompositeElement root = ASTFactory.composite(XML_MARKUP_DECL);

    OldXmlLexer originalLexer = new OldXmlLexer();
    final Lexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(XML_WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(text, 0, text.length(), _OldXmlLexer.DOCTYPE);

    parseMarkupContent(lexer, root);
    while (lexer.getTokenType() != null) {
      final TreeElement children;

      if (lexer.getTokenType() == XML_ENTITY_REF_TOKEN) {
        children = parseEntityRef(lexer);
      }
      else if (lexer.getTokenType() == XML_ENTITY_DECL_START) {
        children = parseEntityDecl(lexer);
      }
      else {
        children = createTokenElement(lexer, myContext.getCharTable());
        lexer.advance();
      }

      root.rawAddChildren(children);
    }

    originalLexer.start(text, 0, text.length(), _OldXmlLexer.DOCTYPE);
    insertMissingTokens(root, originalLexer, 0, text.length(), _OldXmlLexer.DOCTYPE, WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);

    return root;

  }

  public void parseAttrValue(CompositeElement element, Lexer lexer) {
    while(lexer.getTokenType() != null) {
      addToken(element, lexer);
    }
  }

  public static class WhiteSpaceAndCommentsProcessor implements TokenProcessor {
    public static final TokenProcessor INSTANCE = new WhiteSpaceAndCommentsProcessor();

    private WhiteSpaceAndCommentsProcessor() {
    }

    public TreeElement process(Lexer lexer, ParsingContext context) {
      TreeElement first = null;
      TreeElement last = null;
      while (isTokenValid(lexer.getTokenType())) {
        TreeElement tokenElement;

        IElementType type = lexer.getTokenType();
        if (!XML_WHITE_SPACE_OR_COMMENT_BIT_SET.contains(type)) {
          LOG.error("Missed token should be white space or comment:" + type);
          throw new RuntimeException();
        }

        if (lexer.getTokenType() == XML_COMMENT_START) {
          tokenElement = parseComment(lexer, context);
        }
        else {
          tokenElement = createTokenElement(lexer, context.getCharTable());
          lexer.advance();
        }

        if (last != null) {
          last.setTreeNext(tokenElement);
          tokenElement.setTreePrev(last);
          last = tokenElement;
        }
        else {
          first = last = tokenElement;
        }
      }
      return first;
    }

    public boolean isTokenValid(IElementType tokenType) {
      return tokenType != null && XML_WHITE_SPACE_OR_COMMENT_BIT_SET.contains(tokenType);
    }

    private TreeElement parseComment(Lexer lexer, ParsingContext context) {
      final CompositeElement comment = ASTFactory.composite(XML_COMMENT);

      while (lexer.getTokenType() != null && XML_COMMENT_BIT_SET.contains(lexer.getTokenType())) {
        final TreeElement tokenElement = createTokenElement(lexer, context.getCharTable());
        lexer.advance();
        comment.rawAddChildren(tokenElement);
      }

      return comment;
    }
  }

  public static void insertMissingTokens(CompositeElement root,
                                         Lexer lexer,
                                         int startOffset,
                                         int endOffset,
                                         int state,
                                         TokenProcessor processor,
                                         ParsingContext context) {
    if (state < 0) {
      lexer.start(lexer.getBufferSequence(), startOffset, endOffset);
    }
    else {
      lexer.start(lexer.getBufferSequence(), startOffset, endOffset, state);
    }

    LeafElement leaf = TreeUtil.findFirstLeaf(root);
    if (leaf == null) {
      final TreeElement firstMissing = processor.process(lexer, context);
      if (firstMissing != null) {
        root.rawAddChildren(firstMissing);
      }
      return;
    }
    {
      // Missing in the begining
      final IElementType tokenType = lexer.getTokenType();
      if (tokenType != leaf.getElementType() && processor.isTokenValid(tokenType)) {
        final TreeElement firstMissing = processor.process(lexer, context);
        if (firstMissing != null) {
          root.getFirstChildNode().rawInsertBeforeMe(firstMissing);
        }
      }
      passTokenOrChameleon(leaf, lexer);
    }
    // Missing in tree body
    insertMissingTokensInTreeBody(leaf, lexer, processor, context, null);
    if (lexer.getTokenType() != null) {
      // whitespaces at the end of the file
      final TreeElement firstMissing = processor.process(lexer, context);
      if (firstMissing != null) {
        ASTNode current = root;
        while (current instanceof CompositeElement) {
          if (current.getUserData(TreeUtil.UNCLOSED_ELEMENT_PROPERTY) != null) break;
          current = current.getLastChildNode();
        }
        if (current instanceof CompositeElement) {
          ((CompositeElement)current).rawAddChildren(firstMissing);
        }
        else {
          root.getLastChildNode().rawInsertAfterMe(firstMissing);
        }
      }
    }
  }

  public static void insertMissingTokensInTreeBody(TreeElement leaf, Lexer lexer,
                                                   TokenProcessor processor,
                                                   ParsingContext context,
                                                   ASTNode endToken) {
    final TreeUtil.CommonParentState commonParents = new TreeUtil.CommonParentState();
    while (leaf != null) {
      commonParents.strongWhiteSpaceHolder = null;
      final IElementType tokenType = lexer.getTokenType();
      final TreeElement next = TreeUtil.nextLeaf(leaf, commonParents, null, false);

      if (next == null || tokenType == null || next == endToken) break;
      if (tokenType != next.getElementType() && processor.isTokenValid(tokenType)) {
        final TreeElement firstMissing = processor.process(lexer, context);
        final CompositeElement unclosedElement = commonParents.strongWhiteSpaceHolder;
        if (unclosedElement != null) {
          if (commonParents.isStrongElementOnRisingSlope || unclosedElement.getFirstChildNode() == null) {
            unclosedElement.rawAddChildren(firstMissing);
          }
          else {
            unclosedElement.getFirstChildNode().rawInsertBeforeMe(firstMissing);
          }
        }
        else {
          final ASTNode insertBefore = commonParents.nextLeafBranchStart;
          TreeElement insertAfter = commonParents.startLeafBranchStart;
          TreeElement current = commonParents.startLeafBranchStart;
          while (current != insertBefore) {
            final TreeElement treeNext = current.getTreeNext();
            if (treeNext == insertBefore) {
              insertAfter = current;
              break;
            }
            if (treeNext.getUserData(TreeUtil.UNCLOSED_ELEMENT_PROPERTY) != null) {
              insertAfter = null;
              ((CompositeElement)treeNext).rawAddChildren(firstMissing);
              break;
            }
            current = treeNext;
          }
          if (insertAfter != null) insertAfter.rawInsertAfterMe(firstMissing);
        }
      }
      passTokenOrChameleon(next, lexer);
      leaf = next;
    }
  }

  private static void passTokenOrChameleon(final ASTNode next, Lexer lexer) {
    if (next instanceof LeafElement && next instanceof OuterLanguageElement) {
      final int endOfChameleon = next.getTextLength() + lexer.getTokenStart();
      while (lexer.getTokenType() != null && lexer.getTokenEnd() < endOfChameleon) {
        lexer.advance();
      }
    }
    lexer.advance();
  }


}
