package com.intellij.psi.xml;

import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.lexer.OldXmlLexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.source.parsing.xml.DTDMarkupParser;
import com.intellij.psi.impl.source.parsing.xml.DTDParser;
import com.intellij.psi.impl.source.tree.CharTableBasedLeafElementImpl;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.tree.IChameleonElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.xml.IXmlElementType;


public interface XmlElementType extends XmlTokenType {
  IElementType XML_DOCUMENT = new IXmlElementType("XML_DOCUMENT");
  IElementType XML_PROLOG = new IXmlElementType("XML_PROLOG");
  IElementType XML_DECL = new IXmlElementType("XML_DECL");
  IElementType XML_DOCTYPE = new IXmlElementType("XML_DOCTYPE");
  IElementType XML_ATTRIBUTE = new IXmlElementType("XML_ATTRIBUTE");
  IElementType XML_COMMENT = new IXmlElementType("XML_COMMENT");
  IElementType XML_TAG = new IXmlElementType("XML_TAG");
  IElementType XML_ELEMENT_DECL = new IXmlElementType("XML_ELEMENT_DECL");
  IElementType XML_CONDITIONAL_SECTION = new IXmlElementType("XML_CONDITIONAL_SECTION");

  IElementType XML_ATTLIST_DECL = new IXmlElementType("XML_ATTLIST_DECL");
  IElementType XML_NOTATION_DECL = new IXmlElementType("XML_NOTATION_DECL");
  IElementType XML_ENTITY_DECL = new IXmlElementType("XML_ENTITY_DECL");
  IElementType XML_ELEMENT_CONTENT_SPEC = new IXmlElementType("XML_ELEMENT_CONTENT_SPEC");
  IElementType XML_ATTRIBUTE_DECL = new IXmlElementType("XML_ATTRIBUTE_DECL");
  IElementType XML_ATTRIBUTE_VALUE = new IXmlElementType("XML_ATTRIBUTE_VALUE");
  IElementType XML_ENTITY_REF = new IXmlElementType("XML_ENTITY_REF");
  IElementType XML_ENUMERATED_TYPE = new IXmlElementType("XML_ENUMERATED_TYPE");
  IElementType XML_PROCESSING_INSTRUCTION = new IXmlElementType("XML_PROCESSING_INSTRUCTION");
  IElementType XML_CDATA = new IXmlElementType("XML_CDATA");
  IElementType XML_DTD_DECL = new IXmlElementType("XML_DTD_DECL");
  IElementType XML_WHITE_SPACE_HOLDER = new IXmlElementType("XML_WHITE_SPACE_HOLDER");

  //todo: move to html
  IElementType HTML_DOCUMENT = new IXmlElementType("HTML_DOCUMENT");
  IElementType HTML_TAG = new IXmlElementType("HTML_TAG");
  IFileElementType HTML_FILE = new IFileElementType(StdLanguages.HTML);

  IElementType XML_TEXT = new IXmlElementType("XML_TEXT");


  IFileElementType XML_FILE = new IFileElementType(StdLanguages.XML);
  IElementType XHTML_FILE = new IFileElementType(StdLanguages.XHTML);


  IElementType DTD_FILE = new IChameleonElementType("DTD_FILE", StdLanguages.DTD){
    public ASTNode parseContents(ASTNode chameleon) {
      final CharSequence chars = ((CharTableBasedLeafElementImpl)chameleon).getInternedText();
      final DTDParser parser = new DTDParser();
      return parser.parse(chars, 0, chars.length(), SharedImplUtil.findCharTableByTree(chameleon), SharedImplUtil.getManagerByTree(chameleon));
    }
    public boolean isParsable(CharSequence buffer, final Project project) {return true;}
  };

  IElementType XML_MARKUP = new IChameleonElementType("XML_MARKUP_DECL", StdLanguages.XML){
    public ASTNode parseContents(ASTNode chameleon) {
      final CharSequence chars = ((CharTableBasedLeafElementImpl)chameleon).getInternedText();
      final DTDMarkupParser parser = new DTDMarkupParser();
      return parser.parse(chars, 0, chars.length(), SharedImplUtil.findCharTableByTree(chameleon), SharedImplUtil.getManagerByTree(chameleon));
    }

    public boolean isParsable(CharSequence buffer, final Project project) {
      final OldXmlLexer oldXmlLexer = new OldXmlLexer();
      oldXmlLexer.start(buffer, 0, buffer.length(),0);

      while(oldXmlLexer.getTokenType() != null && oldXmlLexer.getTokenEnd() != buffer.length()){
        if(oldXmlLexer.getTokenType() == XmlTokenType.XML_MARKUP_END) return false;
        oldXmlLexer.advance();
      }
      return true;
    }
  };
}
