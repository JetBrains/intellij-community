package com.intellij.psi.impl.source.parsing.xml;

import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.lang.ASTNode;
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerPosition;
import com.intellij.lexer._OldXmlLexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.ParsingContext;
import com.intellij.psi.impl.source.parsing.ParseUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.CharTable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Mike
 */
public class XmlParsing implements ElementType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.xml.XmlParser");

  public static final TokenSet XML_WHITE_SPACE_OR_COMMENT_BIT_SET =
    TokenSet.create(new IElementType[]{XML_WHITE_SPACE, XML_COMMENT_START, XML_COMMENT_CHARACTERS,
                                                                                        XML_COMMENT_END, XML_BAD_CHARACTER});

  public static final TokenSet XML_COMMENT_BIT_SET =
    TokenSet.create(new IElementType[]{XML_COMMENT_START, XML_COMMENT_CHARACTERS, XML_COMMENT_END});

  private ParsingContext myContext;

  public XmlParsing(ParsingContext context) {
    myContext = context;
  }


  public TreeElement parse(Lexer originalLexer, char[] buffer, int startOffset, int endOffset, PsiManager manager) {
    final Lexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(XML_WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(buffer, startOffset, endOffset);

    final FileElement dummyRoot = new DummyHolder(manager, null, myContext.getCharTable()).getTreeElement();

    CompositeElement root = Factory.createCompositeElement(XML_DOCUMENT);
    TreeUtil.addChildren(dummyRoot, root);
    TreeUtil.addChildren(root, parseProlog(lexer));
    parseGenericXml(lexer, root, new HashSet<String>());

    ParseUtil.insertMissingTokens(root,
                                  originalLexer,
                                  startOffset,
                                  endOffset,
                                  -1, WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return root;
  }

  public static ASTNode parseTag(Lexer lexer, char[] buffer, int startOffset, int endOffset, CharTable table, ASTNode parent) {
    XmlParsingContext context = new XmlParsingContext(table);
    FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(XML_WHITE_SPACE_OR_COMMENT_BIT_SET));
    filterLexer.start(buffer, startOffset, endOffset);
    final FileElement holderElement = new DummyHolder(SharedImplUtil.getManagerByTree(parent), null, table).getTreeElement();
    final Set<String> names = new HashSet<String>();
    while (parent instanceof XmlTag) {
      names.add(((XmlTag)parent).getName());
      parent = parent.getTreeParent();
    }

    context.getXmlParsing()._parseTag(holderElement, filterLexer, names);
    while (filterLexer.getTokenType() != null) {
      context.getXmlParsing().addToken(holderElement, filterLexer);
    }
    ParseUtil.insertMissingTokens(holderElement, lexer, startOffset, endOffset, -1, WhiteSpaceAndCommentsProcessor.INSTANCE, context);
    ASTNode result = holderElement.getFirstChildNode();
    return result;
  }

  public void parseGenericXml(Lexer lexer, CompositeElement root, Set<String> names) {
    boolean rootTagChecked = false;
    IElementType tokenType;

    while ((tokenType = lexer.getTokenType()) != null) {
      if (tokenType == XML_ATTLIST_DECL_START) {
        TreeUtil.addChildren(root, parseAttlistDecl(lexer));
      }
      else if (tokenType == XML_ELEMENT_DECL_START) {
        TreeUtil.addChildren(root, parseElementDecl(lexer));
      }
      else if (tokenType == XML_ENTITY_DECL_START) {
        TreeUtil.addChildren(root, parseEntityDecl(lexer));
      }
      else if (tokenType == XML_NOTATION_DECL_START) {
        TreeUtil.addChildren(root, parseNotationDecl(lexer));
      }
      else if (tokenType == XML_ENTITY_REF_TOKEN) {
        TreeUtil.addChildren(root, parseEntityRef(lexer));
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
  }

  public TreeElement parseNotationDecl(Lexer lexer) {
    CompositeElement decl = Factory.createCompositeElement(ElementType.XML_NOTATION_DECL);

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
    CompositeElement decl = Factory.createCompositeElement(ElementType.XML_ENTITY_DECL);

    if (lexer.getTokenType() != XML_ENTITY_DECL_START) {
      return decl;
    }

    addToken(decl, lexer);

    if (lexer.getTokenType() == XML_PERCENT) {
      addToken(decl, lexer);
    }

    if (!parseName(decl, lexer)) {
      return decl;
    }

    parseEntityDeclContent(decl, lexer);

    if (lexer.getTokenType() != null) {
      addToken(decl, lexer);
    }

    return decl;
  }

  public void parseEntityDeclContent(CompositeElement decl, Lexer lexer) {
    while (lexer.getTokenType() != XML_TAG_END && lexer.getTokenType() != null) {
      if (lexer.getTokenType() == XML_ATTRIBUTE_VALUE_START_DELIMITER) {
        parseAttributeValue(decl, lexer);
      }
      else {
        addToken(decl, lexer);
      }
    }
  }

  private boolean parseConditionalSection(CompositeElement parent, Lexer lexer) {
    if (lexer.getTokenType() != XML_CONDITIONAL_SECTION_START) {
      return false;
    }
    CompositeElement conditionalSection = Factory.createCompositeElement(XML_CONDITIONAL_SECTION);
    TreeUtil.addChildren(parent, conditionalSection);

    addToken(conditionalSection, lexer);
    IElementType tokenType = lexer.getTokenType();
    if (tokenType != XML_CONDITIONAL_IGNORE &&
        tokenType != XML_CONDITIONAL_INCLUDE &&
        tokenType != XML_ENTITY_REF_TOKEN) {
      return true;
    }

    if (tokenType == XML_ENTITY_REF_TOKEN) {
      TreeUtil.addChildren(conditionalSection, parseEntityRef(lexer) );
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
    CompositeElement tag = Factory.createCompositeElement(XML_PROCESSING_INSTRUCTION);
    TreeUtil.addChildren(parent, tag);

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

    CompositeElement tag = Factory.createCompositeElement(XML_TAG);
    TreeUtil.addChildren(parent, tag);

    addToken(tag, lexer);

    if (lexer.getTokenType() != XML_TAG_NAME) {
      return true;
    }

    String openedName = StringFactory.createStringFromConstantArray(lexer.getBuffer(), lexer.getTokenStart(),
                                                                      lexer.getTokenEnd() - lexer.getTokenStart());
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
            text = Factory.createCompositeElement(XML_TEXT);
            TreeUtil.addChildren(tag, text);
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
          TreeUtil.addChildren(tag, parseEntityRef(lexer));
        }
        else {
          break;
        }
      }
      if (setFlag) {
        names.remove(openedName);
      }

      final LexerPosition pos = lexer.getCurrentPosition();

      if (lexer.getTokenType() != XML_END_TAG_START) {
        TreeUtil.insertAfter(tagEnd, Factory.createErrorElement(XmlErrorMessages.message("element.is.not.closed")));

        return false;
      }
      TreeElement endTagStart = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
      lexer.advance();

      if (lexer.getTokenType() != XML_TAG_NAME) {
        TreeUtil.addChildren(tag, endTagStart);
        return true;
      }

      String closingName = StringFactory.createStringFromConstantArray(lexer.getBuffer(), lexer.getTokenStart(),
                                                                         lexer.getTokenEnd() - lexer.getTokenStart());

      if (!closingName.equals(openedName) && names.contains(closingName)) {
        lexer.restore(pos);
        if (tagEnd != null) {
          final TreeElement start = tagEnd.getTreeNext();
          tagEnd.setTreeNext(null);
          if (start != null) {
            TreeUtil.addChildren(parent, start);
          }
        }
        TreeUtil.insertAfter(tagEnd, Factory.createErrorElement(XmlErrorMessages.message("element.is.not.closed")));
        return true;
      }

      TreeUtil.addChildren(tag, endTagStart);
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
      TreeUtil.insertAfter((TreeElement)tag.getLastChildNode(), Factory.createErrorElement(XmlErrorMessages.message("element.is.not.closed")));
    }

    return true;
  }

  private TreeElement parseEntityRef(Lexer lexer) {
    CompositeElement ref = Factory.createCompositeElement(XML_ENTITY_REF);

    if (lexer.getTokenType() == XML_ENTITY_REF_TOKEN) {
      addToken(ref, lexer);
    }

    return ref;
  }

  private TreeElement parseProlog(Lexer lexer) {
    CompositeElement prolog = Factory.createCompositeElement(XML_PROLOG);

    while (parseProcessingInstruction(prolog, lexer)) {
    }

    if (lexer.getTokenType() == XML_DECL_START) {
      TreeUtil.addChildren(prolog, parseDecl(lexer));
    }

    while (parseProcessingInstruction(prolog, lexer)) {
    }

    if (lexer.getTokenType() == XML_DOCTYPE_START) {
      TreeUtil.addChildren(prolog, parseDocType(lexer));
    }

    while (parseProcessingInstruction(prolog, lexer)) {
    }

    return prolog;
  }

  private TreeElement parseDocType(Lexer lexer) {
    CompositeElement docType = Factory.createCompositeElement(XML_DOCTYPE);

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
      TreeUtil.addChildren(docType, parseMarkupDecl(lexer));
    }

    if (lexer.getTokenType() != XML_DOCTYPE_END) {
      return docType;
    }

    addToken(docType, lexer);

    return docType;
  }

  private TreeElement parseMarkupDecl(Lexer lexer) {
    CompositeElement decl = Factory.createCompositeElement(XML_MARKUP_DECL);

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
        TreeUtil.addChildren(decl, parseElementDecl(lexer));
      }
      else if (tokenType == XML_ATTLIST_DECL_START) {
        TreeUtil.addChildren(decl, parseAttlistDecl(lexer));
      }
      else if (tokenType == XML_ENTITY_DECL_START) {
        TreeUtil.addChildren(decl, parseEntityDecl(lexer));
      }
      else if (tokenType == XML_NOTATION_DECL_START) {
        TreeUtil.addChildren(decl, parseNotationDecl(lexer));
      } else if (tokenType == XML_ENTITY_REF_TOKEN) {
        TreeUtil.addChildren(decl, parseEntityRef(lexer));
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
    CompositeElement decl = Factory.createCompositeElement(ElementType.XML_ELEMENT_DECL);

    if (lexer.getTokenType() != XML_ELEMENT_DECL_START) {
      return decl;
    }

    addToken(decl, lexer);

    if (!parseName(decl, lexer)) {
      return decl;
    }

    parseElementContentSpec(decl, lexer);

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
      TreeUtil.addChildren(decl, parseEntityRef(lexer));
      return true;
    }

    return false;
  }

  public ASTNode parseElementContentSpec(CompositeElement parent, Lexer lexer) {
    CompositeElement spec = Factory.createCompositeElement(XML_ELEMENT_CONTENT_SPEC);
    TreeUtil.addChildren(parent, spec);

    while (
      lexer.getTokenType() != null &&
      lexer.getTokenType() != XML_TAG_END &&
      lexer.getTokenType() != XML_START_TAG_START &&
      lexer.getTokenType() != XML_ELEMENT_DECL_START) {
      if (lexer.getTokenType() == XML_ENTITY_REF_TOKEN) {
        TreeUtil.addChildren(spec, parseEntityRef(lexer));
      }
      else {
        addToken(spec, lexer);
      }
    }

    return spec;
  }

  private TreeElement parseAttlistDecl(Lexer lexer) {
    CompositeElement decl = Factory.createCompositeElement(XML_ATTLIST_DECL);

    if (lexer.getTokenType() != XML_ATTLIST_DECL_START) {
      return decl;
    }

    addToken(decl, lexer);

    if (lexer.getTokenType() != XML_NAME) {
      return decl;
    }

    addToken(decl, lexer);

    parseAttlistContent(decl, lexer);

    if (lexer.getTokenType() == XML_TAG_END) {
      addToken(decl, lexer);
    }

    return decl;
  }

  public void parseAttlistContent(CompositeElement parent, Lexer lexer) {
    while (true) {
      if (lexer.getTokenType() == XML_ENTITY_REF_TOKEN) {
        TreeUtil.addChildren(parent, parseEntityRef(lexer));
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

    CompositeElement decl = Factory.createCompositeElement(ElementType.XML_ATTRIBUTE_DECL);
    TreeUtil.addChildren(parent, decl);

    addToken(decl, lexer);

    return parseAttributeContentSpec(decl, lexer);
  }

  public boolean parseAttributeContentSpec(CompositeElement parent, Lexer lexer) {
    if (parseName(parent, lexer)) {
    }
    else if (lexer.getTokenType() == XML_LEFT_PAREN) {
      TreeUtil.addChildren(parent, parseEnumeratedType(lexer));
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
    CompositeElement enumeratedType = Factory.createCompositeElement(XML_ENUMERATED_TYPE);
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
        TreeUtil.addChildren(enumeratedType, parseEntityRef(lexer));
      continue;
      }

      if (lexer.getTokenType() != XML_NAME && lexer.getTokenType() != XML_BAR) break;
      addToken(enumeratedType, lexer);
    }
  }

  TreeElement parseDecl(Lexer lexer) {
    CompositeElement decl = Factory.createCompositeElement(XML_DECL);

    if (lexer.getTokenType() != XML_DECL_START) {
      return decl;
    }
    addToken(decl, lexer);

    parseAttributeList(decl, lexer);

    if (lexer.getTokenType() == XML_DECL_END) {
      addToken(decl, lexer);
    }
    else {
      TreeUtil.addChildren(decl, Factory.createErrorElement(XmlErrorMessages.message("expected.prologue.tag.termination.expected")));
    }

    return decl;
  }

  private void parseAttributeList(CompositeElement tag, Lexer lexer) {
    CompositeElement parent = tag;
    int lastPosition = -1;
    while (true) {
      if (lexer.getTokenType() == XML_ENTITY_REF_TOKEN) {
        TreeUtil.addChildren(tag, parseEntityRef(lexer));
      continue;
      }

      if (lexer.getTokenType() != XML_NAME) {
        return;
      }

      if (lastPosition != -1) {
        if (lastPosition == lexer.getTokenStart()) {
          TreeUtil.addChildren(tag, Factory.createErrorElement(XmlErrorMessages.message("expected.whitespace")));
          lastPosition = -1;
        }
      }

      if (tag instanceof XmlTag) {
        CompositeElement attribute = Factory.createCompositeElement(XML_ATTRIBUTE);
        TreeUtil.addChildren(tag, attribute);
        parent = attribute;
      }
      
      addToken(parent, lexer);

      if (lexer.getTokenType() != XML_EQ) {
        TreeUtil.addChildren(parent, Factory.createErrorElement(XmlErrorMessages.message("expected.attribute.eq.sign")));
        continue;
      }

      addToken(parent, lexer);
      
      if (tag instanceof XmlTag) {
        CompositeElement attributeValue = Factory.createCompositeElement(XML_ATTRIBUTE_VALUE);
        TreeUtil.addChildren(parent,attributeValue);
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

    CompositeElement value = Factory.createCompositeElement(XML_ATTRIBUTE_VALUE);

    TreeUtil.addChildren(parent, value);

    addToken(value, lexer);

    while (true) {
      if (lexer.getTokenType() == XML_ATTRIBUTE_VALUE_TOKEN) {
        addToken(value, lexer);
      }
      else if (lexer.getTokenType() == XML_CHAR_ENTITY_REF) {
        addToken(value, lexer);
      }
      else if (lexer.getTokenType() == XML_ENTITY_REF_TOKEN) {
        TreeUtil.addChildren(value, parseEntityRef(lexer));
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
    final TreeElement element = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
    if (element != null) {
      TreeUtil.addChildren(decl, element);
      lexer.advance();
    }
    return element;
  }

  public TreeElement parseMarkupDecl(Lexer originalLexer, char[] text, int start, int end, PsiManager manager) {
    final Lexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(XML_WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(text, start, end, _OldXmlLexer.DOCTYPE);

    final FileElement dummyRoot = new DummyHolder(manager, null, myContext.getCharTable()).getTreeElement();
    parseMarkupContent(lexer, dummyRoot);
    while (lexer.getTokenType() != null) {
      final TreeElement children;

      if(lexer.getTokenType() == XML_ENTITY_REF_TOKEN) {
        children = parseEntityRef(lexer);
      } else if (lexer.getTokenType() == XML_ENTITY_DECL_START) {
        children = parseEntityDecl(lexer);
      } else {
        children = ParseUtil.createTokenElement(lexer, dummyRoot.getCharTable());
        lexer.advance();
      }

      TreeUtil.addChildren(dummyRoot, children);
    }
    originalLexer.start(text, start, end, _OldXmlLexer.DOCTYPE);
    ParseUtil.insertMissingTokens(dummyRoot, originalLexer, start, end, _OldXmlLexer.DOCTYPE,
                                  WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return (TreeElement)dummyRoot.getFirstChildNode();
  }

  public static class WhiteSpaceAndCommentsProcessor implements ParseUtil.TokenProcessor {
    public static final ParseUtil.TokenProcessor INSTANCE = new WhiteSpaceAndCommentsProcessor();

    private WhiteSpaceAndCommentsProcessor() {
    }

    public TreeElement process(Lexer lexer, ParsingContext context) {
      TreeElement first = null;
      TreeElement last = null;
      while (isTokenValid(lexer.getTokenType())) {
        TreeElement tokenElement;

        IElementType type = lexer.getTokenType();
        if (!XML_WHITE_SPACE_OR_COMMENT_BIT_SET.contains(type)) {
          LOG.assertTrue(false, "Missed token should be white space or comment:" + type);
          throw new RuntimeException();
        }

        if (lexer.getTokenType() == XML_COMMENT_START) {
          tokenElement = parseComment(lexer, context);
        }
        else {
          tokenElement = ParseUtil.createTokenElement(lexer, context.getCharTable());
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
      final CompositeElement comment = Factory.createCompositeElement(ElementType.XML_COMMENT);

      while (lexer.getTokenType() != null && XML_COMMENT_BIT_SET.contains(lexer.getTokenType())) {
        final TreeElement tokenElement = ParseUtil.createTokenElement(lexer, context.getCharTable());
        lexer.advance();
        TreeUtil.addChildren(comment, tokenElement);
      }

      return comment;
    }
  }
}
