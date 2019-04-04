// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.parsing.xml;

import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.lang.dtd.DTDLanguage;
import com.intellij.lexer.DtdLexer;
import com.intellij.lexer._DtdLexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlEntityDecl;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mike
 */
public class DtdParsing extends XmlParsing implements XmlElementType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.xml.XmlParser");

  private final IElementType myRootType;
  public static final XmlEntityDecl.EntityContextType TYPE_FOR_MARKUP_DECL = XmlEntityDecl.EntityContextType.ELEMENT_CONTENT_SPEC;
  private final XmlEntityDecl.EntityContextType myContextType;

  public DtdParsing(IElementType root, XmlEntityDecl.EntityContextType contextType, PsiBuilder builder) {
    super(builder);
    myRootType = root;
    myContextType = contextType;
    myBuilder.enforceCommentTokens(TokenSet.EMPTY);
  }

  public DtdParsing(CharSequence chars,
                    final IElementType type,
                    final XmlEntityDecl.EntityContextType contextType,
                    PsiFile contextFile
  ) {
    this(
      type,
      contextType,
      PsiBuilderFactory.getInstance().createBuilder(
        LanguageParserDefinitions.INSTANCE.forLanguage(DTDLanguage.INSTANCE),
        new DtdLexer(false) {
          final int myInitialState = getLexerInitialState(type, contextType);
          @Override
          public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
            super.start(buffer, startOffset, endOffset, myInitialState);
          }
        }, chars
      )
    );
    if (contextFile != null) myBuilder.putUserData(FileContextUtil.CONTAINING_FILE_KEY, contextFile);
  }

  public ASTNode parse() {
    final PsiBuilder.Marker root = myBuilder.mark();

    if (myRootType == XML_MARKUP_DECL) {
      parseTopLevelMarkupDecl();
      root.done(myRootType);
      return myBuilder.getTreeBuilt();
    }

    PsiBuilder.Marker document = null;
    if (myRootType == DTD_FILE) {
      document = myBuilder.mark();
      parseProlog();
    } 

    switch (myContextType) {
      case GENERIC_XML:
        parseGenericXml();
        break;
      case ELEMENT_CONTENT_SPEC:
        doParseContentSpec(true);
        break;
      case ATTLIST_SPEC:
        parseAttlistContent();
        break;
      case ATTR_VALUE:
        parseAttrValue();
      case ATTRIBUTE_SPEC:
        parseAttributeContentSpec();
        break;
      case ENTITY_DECL_CONTENT:
        parseEntityDeclContent();
        break;
      case ENUMERATED_TYPE:
        parseEnumeratedTypeContent();
        break;
    }

    while(!myBuilder.eof()) myBuilder.advanceLexer();
    if (document != null) document.done(XML_DOCUMENT);
    root.done(myRootType);
    ASTNode astNode = myBuilder.getTreeBuilt();

    if (myRootType != DTD_FILE) {
      PsiFile file = myBuilder.getUserData(FileContextUtil.CONTAINING_FILE_KEY);
      if (file != null) {
        final DummyHolder result = DummyHolderFactory.createHolder(file.getManager(), DTDLanguage.INSTANCE, file);
        final FileElement holder = result.getTreeElement();
        holder.rawAddChildren((TreeElement)astNode);
      }
    }

    return astNode;
  }

  private static int getLexerInitialState(IElementType rootNodeType, XmlEntityDecl.EntityContextType context) {
    short state = 0;

    switch (context) {
      case ELEMENT_CONTENT_SPEC:
      case ATTRIBUTE_SPEC:
      case ATTLIST_SPEC:
      case ENUMERATED_TYPE:
      case ENTITY_DECL_CONTENT:
      {
        state = _DtdLexer.DOCTYPE_MARKUP;
        break;
      }

      case ATTR_VALUE:
      case GENERIC_XML: {
        break;
      }


      default: LOG.error("context: " + context);
    }

    if (rootNodeType == XML_MARKUP_DECL && context == TYPE_FOR_MARKUP_DECL) {
      state = _DtdLexer.DOCTYPE;
    }
    return state;
  }

  private void parseGenericXml() {
    IElementType tokenType;

    while ((tokenType = myBuilder.getTokenType()) != null) {
      if (tokenType == XML_ATTLIST_DECL_START) {
        parseAttlistDecl();
      }
      else if (tokenType == XML_ELEMENT_DECL_START) {
        parseElementDecl();
      }
      else if (tokenType == XML_ENTITY_DECL_START) {
        parseEntityDecl();
      }
      else if (tokenType == XML_NOTATION_DECL_START) {
        parseNotationDecl();
      }
      else if (tokenType == XML_ENTITY_REF_TOKEN) {
        parseEntityRef();
      }
      else if (parseProcessingInstruction()) {
      }
      else if (tokenType == XML_START_TAG_START) {
        parseTag(false);
      } else if (isCommentToken(tokenType)) {
        parseComment();
      }
      else if (parseConditionalSection()) {
      }
      else if (tokenType != null) {
        addToken();
      }
    }
  }

  private void parseNotationDecl() {
    if (myBuilder.getTokenType() != XML_NOTATION_DECL_START) {
      return;
    }

    PsiBuilder.Marker decl = myBuilder.mark();
    addToken();

    if (!parseName()) {
      decl.done(XML_NOTATION_DECL);
      return;
    }

    parseEntityDeclContent();

    if (myBuilder.getTokenType() != null) {
      addToken();
    }

    decl.done(XML_NOTATION_DECL);
  }

  private void parseEntityDecl() {
    if (myBuilder.getTokenType() != XML_ENTITY_DECL_START) {
      return;
    }

    PsiBuilder.Marker decl = myBuilder.mark();
    addToken();

    if (myBuilder.getTokenType() == XML_PERCENT) {
      addToken();
    }

    if (parseCompositeName()) {
      decl.done(XML_ENTITY_DECL);
      return;
    }

    parseEntityDeclContent();

    if (myBuilder.getTokenType() != null) {
      addToken();
    }

    decl.done(XML_ENTITY_DECL);
  }

  private boolean parseCompositeName() {
    if (!parseName()) {
      if (myBuilder.getTokenType() == XML_LEFT_PAREN) {
        parseGroup();
      } else {
        myBuilder.error(XmlErrorMessages.message("dtd.parser.message.name.expected"));
        return true;
      }
    }
    return false;
  }

  private void parseEntityDeclContent() {
    IElementType tokenType = myBuilder.getTokenType();
    if (tokenType != XML_ATTRIBUTE_VALUE_START_DELIMITER &&
        tokenType != XML_DOCTYPE_PUBLIC &&
        tokenType != XML_DOCTYPE_SYSTEM) {
      myBuilder.error(XmlErrorMessages.message("dtd.parser.message.literal.public.system.expected"));
      return;
    }

    while (tokenType != XML_TAG_END && tokenType != null) {
      if (tokenType == XML_ATTRIBUTE_VALUE_START_DELIMITER) {
        parseAttributeValue();
      }
      else {
        addToken();
      }

      tokenType = myBuilder.getTokenType();
    }
  }

  private boolean parseConditionalSection() {
    if (myBuilder.getTokenType() != XML_CONDITIONAL_SECTION_START) {
      return false;
    }
    
    PsiBuilder.Marker conditionalSection = myBuilder.mark();

    addToken();
    IElementType tokenType = myBuilder.getTokenType();
    if (tokenType != XML_CONDITIONAL_IGNORE &&
        tokenType != XML_CONDITIONAL_INCLUDE &&
        tokenType != XML_ENTITY_REF_TOKEN) {
      conditionalSection.done(XML_CONDITIONAL_SECTION);
      return true;
    }

    if (tokenType == XML_ENTITY_REF_TOKEN) {
      parseEntityRef();
    } else {
      addToken();
    }

    if (myBuilder.getTokenType() != XML_MARKUP_START) {
      conditionalSection.done(XML_CONDITIONAL_SECTION);
      return true;
    }

    parseMarkupContent();

    if (myBuilder.getTokenType() != XML_CONDITIONAL_SECTION_END) {
      conditionalSection.done(XML_CONDITIONAL_SECTION);
      return true;
    }
    addToken();
    conditionalSection.done(XML_CONDITIONAL_SECTION);
    return true;
  }

  private boolean parseProcessingInstruction() {
    if (myBuilder.getTokenType() != XML_PI_START) {
      return false;
    }
    PsiBuilder.Marker tag = myBuilder.mark();

    addToken();
    if (myBuilder.getTokenType() != XML_PI_TARGET) {
      tag.done(XML_PROCESSING_INSTRUCTION);
      return true;
    }
    addToken();
    if (myBuilder.getTokenType() != XML_PI_END) {
      tag.done(XML_PROCESSING_INSTRUCTION);
      return true;
    }
    addToken();
    tag.done(XML_PROCESSING_INSTRUCTION);
    return true;
  }

  private void parseEntityRef() {
    PsiBuilder.Marker ref = myBuilder.mark();

    if (myBuilder.getTokenType() == XML_ENTITY_REF_TOKEN) {
      addToken();
    }

    ref.done(XML_ENTITY_REF);
  }

  private void parseProlog() {
    PsiBuilder.Marker prolog = myBuilder.mark();

    while (parseProcessingInstruction()) {}

    if (myBuilder.getTokenType() == XML_DECL_START) {
      parseDecl();
    }

    while (parseProcessingInstruction()) {}

    if (myBuilder.getTokenType() == XML_DOCTYPE_START) {
      parseDocType();
    }

    while (parseProcessingInstruction()) {}

    prolog.done(XML_PROLOG);
  }

  private void parseDocType() {
    if (myBuilder.getTokenType() != XML_DOCTYPE_START) {
      return;
    }

    PsiBuilder.Marker docType = myBuilder.mark();
    addToken();

    if (myBuilder.getTokenType() != XML_NAME) {
      docType.done(XML_DOCTYPE);
      return;
    }

    addToken();

    if (myBuilder.getTokenType() == XML_DOCTYPE_SYSTEM) {
      addToken();

      if (myBuilder.getTokenType() == XML_ATTRIBUTE_VALUE_TOKEN) {
        addToken();
      }
    }
    else if (myBuilder.getTokenType() == XML_DOCTYPE_PUBLIC) {
      addToken();

      if (myBuilder.getTokenType() == XML_ATTRIBUTE_VALUE_TOKEN) {
        addToken();
      }

      if (myBuilder.getTokenType() == XML_ATTRIBUTE_VALUE_TOKEN) {
        addToken();
      }
    }

    if (myBuilder.getTokenType() == XML_MARKUP_START) {
      parseMarkupDecl();
    }

    if (myBuilder.getTokenType() != XML_DOCTYPE_END) {
      docType.done(XML_DOCTYPE);
      return;
    }

    addToken();

    docType.done(XML_DOCTYPE);
  }

  private void parseMarkupDecl() {
    PsiBuilder.Marker decl = myBuilder.mark();
    parseMarkupContent();

    decl.done(XML_MARKUP_DECL);
  }

  private void parseMarkupContent() {
    IElementType tokenType = myBuilder.getTokenType();
    if (tokenType == XML_MARKUP_START) {
      addToken();
    }

    while (true) {
      tokenType = myBuilder.getTokenType();
      
      if (tokenType == XML_ELEMENT_DECL_START) {
        parseElementDecl();
      }
      else if (tokenType == XML_ATTLIST_DECL_START) {
        parseAttlistDecl();
      }
      else if (tokenType == XML_ENTITY_DECL_START) {
        parseEntityDecl();
      }
      else if (tokenType == XML_NOTATION_DECL_START) {
        parseNotationDecl();
      } else if (tokenType == XML_ENTITY_REF_TOKEN) {
        parseEntityRef();
      } else if (tokenType == XML_COMMENT_START) {
        parseComment();
      }
      else if (parseConditionalSection()) {
      }
      else {
        break;
      }
    }

    if (tokenType == XML_MARKUP_END) {
      addToken();
    }
  }

  private void parseElementDecl() {
    if (myBuilder.getTokenType() != XML_ELEMENT_DECL_START) {
      return;
    }

    PsiBuilder.Marker decl = myBuilder.mark();

    addToken();

    if (parseCompositeName()) {
      decl.done(XML_ELEMENT_DECL);
      return;
    }

    doParseContentSpec(false);

    skipTillEndOfBlock();

    decl.done(XML_ELEMENT_DECL);
  }

  private void skipTillEndOfBlock() {
    while (!myBuilder.eof() &&
           myBuilder.getTokenType() != XML_TAG_END &&
           !isAnotherDeclStart(myBuilder.getTokenType())
      ) {
      if (myBuilder.getTokenType() == XML_COMMENT_START) parseComment();
      else addToken();
    }

    if(myBuilder.getTokenType() == XML_TAG_END) addToken();
  }

  private boolean isAnotherDeclStart(IElementType type) {
    return type == XML_ATTLIST_DECL_START || type == XML_ELEMENT_DECL_START;
  }

  private boolean parseName() {
    IElementType type = myBuilder.getTokenType();
    if (type == XML_NAME) {
      addToken();

      return true;
    }

    if (type == XML_ENTITY_REF_TOKEN) {
      parseEntityRef();
      return true;
    }

    return consumeKeywordAsName(type);
  }

  private boolean consumeKeywordAsName(IElementType type) {
    if (type == XML_DOCTYPE_PUBLIC || type == XML_DOCTYPE_SYSTEM || type == XML_CONTENT_EMPTY || type == XML_CONTENT_ANY) {
      myBuilder.remapCurrentToken(XML_NAME);
      addToken();
      return true;
    }
    return false;
  }

  private void doParseContentSpec(boolean topLevel) {
    if (!topLevel && myBuilder.rawLookup(0) != XML_WHITE_SPACE) {
      myBuilder.error(XmlErrorMessages.message("dtd.parser.message.whitespace.expected"));
    } else if (!topLevel) {
      final IElementType tokenType = myBuilder.getTokenType();
      String tokenText;

      if (tokenType != XML_LEFT_PAREN &&
          tokenType != XML_ENTITY_REF_TOKEN &&
          tokenType != XML_CONTENT_ANY &&
          tokenType != XML_CONTENT_EMPTY &&
          (tokenType != XML_NAME || ( !("-".equals(tokenText = myBuilder.getTokenText())) && !"O".equals(tokenText))) // sgml compatibility
        ) {
        PsiBuilder.Marker spec = myBuilder.mark();
        spec.done(XML_ELEMENT_CONTENT_SPEC);
        myBuilder.error(XmlErrorMessages.message("dtd.parser.message.left.paren.or.entityref.or.empty.or.any.expected"));
        return;
      }
    }

    PsiBuilder.Marker spec = myBuilder.mark();
    
    parseElementContentSpecInner(topLevel);

    spec.done(XML_ELEMENT_CONTENT_SPEC);
  }

  private boolean parseElementContentSpecInner(boolean topLevel) {
    IElementType tokenType = myBuilder.getTokenType();
    boolean endedWithDelimiter = false;

    while (
      tokenType != null &&
      tokenType != XML_TAG_END &&
      tokenType != XML_START_TAG_START &&
      tokenType != XML_ELEMENT_DECL_START &&
      tokenType != XML_RIGHT_PAREN &&
      tokenType != XML_COMMENT_START
    ) {
      if (tokenType == XML_BAR && topLevel) {
        addToken();
        tokenType = myBuilder.getTokenType();
        continue;
      } else
      if (tokenType == XML_LEFT_PAREN) {
        if (!parseGroup()) return false;
        endedWithDelimiter = false;
      } else
      if (tokenType == XML_ENTITY_REF_TOKEN) {
        parseEntityRef();
        endedWithDelimiter = false;
      } else if (tokenType == XML_NAME ||
                 tokenType == XML_CONTENT_EMPTY ||
                 tokenType == XML_CONTENT_ANY ||
                 tokenType == XML_PCDATA
                 ) {
        addToken();
        endedWithDelimiter = false;
      }
      else if (consumeKeywordAsName(tokenType)) {
        endedWithDelimiter = false;
      }
      else {
        myBuilder.error(XmlErrorMessages.message("dtd.parser.message.name.or.entity.ref.expected"));
        return false;
      }

      tokenType = myBuilder.getTokenType();

      if (tokenType == XML_STAR ||
          tokenType == XML_PLUS ||
          tokenType == XML_QUESTION
         ) {
        addToken();
        tokenType = myBuilder.getTokenType();

        if (tokenType == XML_PLUS) {
          addToken();
          tokenType = myBuilder.getTokenType();
        }
      }
      if (tokenType == XML_BAR || tokenType == XML_COMMA) {
        addToken();
        tokenType = myBuilder.getTokenType();
        endedWithDelimiter = true;
      }
    }

    if (endedWithDelimiter && tokenType == XML_RIGHT_PAREN) {
      myBuilder.error(XmlErrorMessages.message("dtd.parser.message.name.or.entity.ref.expected"));
    }
    return true;
  }

  private boolean parseGroup() {
    PsiBuilder.Marker group = myBuilder.mark();
    addToken();
    boolean b = parseElementContentSpecInner(false);
    if (b && myBuilder.getTokenType() == XML_RIGHT_PAREN) {
      addToken();
      group.done(XML_ELEMENT_CONTENT_GROUP);
      return true;
    } else if (b) {
      myBuilder.error(XmlErrorMessages.message("dtd.parser.message.rbrace.expected"));
      group.done(XML_ELEMENT_CONTENT_GROUP);
      return false;
    }
    group.done(XML_ELEMENT_CONTENT_GROUP);
    return b;
  }

  private void parseAttlistDecl() {
    if (myBuilder.getTokenType() != XML_ATTLIST_DECL_START) {
      return;
    }

    PsiBuilder.Marker decl = myBuilder.mark();
    addToken();

    if (!parseName()) {
      final IElementType tokenType = myBuilder.getTokenType();
      if (tokenType == XML_LEFT_PAREN) {
        parseGroup();
      } else {
        myBuilder.error(XmlErrorMessages.message("dtd.parser.message.name.expected"));
        decl.done(XML_ATTLIST_DECL);
        return;
      }
    }

    parseAttlistContent();

    skipTillEndOfBlock();

    decl.done(XML_ATTLIST_DECL);
  }

  private void parseAttlistContent() {
    while (true) {
      if (myBuilder.getTokenType() == XML_ENTITY_REF_TOKEN) {
        parseEntityRef();
      }
      else if (myBuilder.getTokenType() == XML_COMMENT_START) {
        parseComment();
      } else if (parseAttributeDecl()) {
      }
      else {
        break;
      }
    }
  }

  private boolean parseAttributeDecl() {
    if (myBuilder.getTokenType() != XML_NAME) {
      return false;
    }

    PsiBuilder.Marker decl = myBuilder.mark();

    addToken();

    final boolean b = parseAttributeContentSpec();
    //if (myBuilder.getTokenType() == XML_COMMENT_START) parseComment();
    decl.done(XML_ATTRIBUTE_DECL);
    return b;
  }

  private boolean parseAttributeContentSpec() {
    if (parseName()) {
    }
    else if (myBuilder.getTokenType() == XML_LEFT_PAREN) {
      parseEnumeratedType();
    }
    else {
      return true;
    }

    if (myBuilder.getTokenType() == XML_ATT_IMPLIED) {
      addToken();
    }
    else if (myBuilder.getTokenType() == XML_ATT_REQUIRED) {
      addToken();
    }
    else if (myBuilder.getTokenType() == XML_ATT_FIXED) {
      addToken();

      if (myBuilder.getTokenType() == XML_ATTRIBUTE_VALUE_START_DELIMITER) {
        parseAttributeValue();
      }
    }
    else if (myBuilder.getTokenType() == XML_ATTRIBUTE_VALUE_START_DELIMITER) {
      parseAttributeValue();
    }

    return true;
  }

  private void parseEnumeratedType() {
    PsiBuilder.Marker enumeratedType = myBuilder.mark();
    addToken();

    parseEnumeratedTypeContent();

    if (myBuilder.getTokenType() == XML_RIGHT_PAREN) {
      addToken();
    }

    enumeratedType.done(XML_ENUMERATED_TYPE);
  }

  private void parseEnumeratedTypeContent() {
    while (true) {
      if (myBuilder.getTokenType() == XML_ENTITY_REF_TOKEN) {
        parseEntityRef();
        continue;
      }

      if (myBuilder.getTokenType() != XML_NAME && myBuilder.getTokenType() != XML_BAR) break;
      addToken();
    }
  }

  private void parseDecl() {
    if (myBuilder.getTokenType() != XML_DECL_START) {
      return;
    }

    PsiBuilder.Marker decl = myBuilder.mark();
    addToken();

    parseAttributeList();

    if (myBuilder.getTokenType() == XML_DECL_END) {
      addToken();
    }
    else {
      myBuilder.error(XmlErrorMessages.message("expected.prologue.tag.termination.expected"));
    }

    decl.done(XML_DECL);
  }

  private void parseAttributeList() {
    int lastPosition = -1;
    while (true) {
      if (myBuilder.getTokenType() == XML_ENTITY_REF_TOKEN) {
        parseEntityRef();
        continue;
      }

      if (myBuilder.getTokenType() != XML_NAME) {
        return;
      }

      if (lastPosition != -1) {
        if (lastPosition == myBuilder.getCurrentOffset()) {
          myBuilder.error(XmlErrorMessages.message("expected.whitespace"));
          lastPosition = -1;
        }
      }

      addToken();

      if (myBuilder.getTokenType() != XML_EQ) {
        myBuilder.error(XmlErrorMessages.message("expected.attribute.eq.sign"));
        continue;
      }

      addToken();

      if (myBuilder.getTokenType() != XML_ATTRIBUTE_VALUE_START_DELIMITER) {
        return;
      }

      addToken();

      if (myBuilder.getTokenType() == XML_ATTRIBUTE_VALUE_TOKEN) {
        addToken();
        
        if (myBuilder.getTokenType() == XML_ATTRIBUTE_VALUE_END_DELIMITER) {
          lastPosition = myBuilder.getCurrentOffset();
          addToken();
        }
        else {
          lastPosition = -1;
        }
      }
      else if (myBuilder.getTokenType() == XML_ATTRIBUTE_VALUE_END_DELIMITER) {
        lastPosition = myBuilder.getCurrentOffset();
        addToken();
      }
      else {
        lastPosition = -1;
      }
    }
  }

  private int parseAttributeValue() {
    if (myBuilder.getTokenType() != XML_ATTRIBUTE_VALUE_START_DELIMITER) {
      return -1;
    }

    PsiBuilder.Marker value = myBuilder.mark();

    addToken();

    while (true) {
      if (myBuilder.getTokenType() == XML_ATTRIBUTE_VALUE_TOKEN) {
        addToken();
      }
      else if (myBuilder.getTokenType() == XML_CHAR_ENTITY_REF) {
        addToken();
      }
      else if (myBuilder.getTokenType() == XML_ENTITY_REF_TOKEN) {
        parseEntityRef();
      }
      else {
        break;
      }
    }

    if (myBuilder.getTokenType() != XML_ATTRIBUTE_VALUE_END_DELIMITER) {
      value.done(XML_ATTRIBUTE_VALUE);
      return -1;
    }

    int tokenEnd = myBuilder.getCurrentOffset();
    addToken();
    value.done(XML_ATTRIBUTE_VALUE);
    return tokenEnd;
  }

  private void addToken() {
    myBuilder.advanceLexer();
  }

  private void parseTopLevelMarkupDecl() {
    parseMarkupContent();
    while (myBuilder.getTokenType() != null) {
      if (myBuilder.getTokenType() == XML_ENTITY_REF_TOKEN) {
        parseEntityRef();
      }
      else if (myBuilder.getTokenType() == XML_ENTITY_DECL_START) {
        parseEntityDecl();
      }
      else {
        myBuilder.advanceLexer();
      }
    }
  }

  private void parseAttrValue() {
    while(myBuilder.getTokenType() != null) {
      if (myBuilder.getTokenType() == XML_ENTITY_REF_TOKEN) {
        parseEntityRef();
      } else {
        addToken();
      }
    }
  }
}
