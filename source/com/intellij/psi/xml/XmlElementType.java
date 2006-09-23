package com.intellij.psi.xml;

import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.lexer.OldXmlLexer;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.impl.source.parsing.tabular.ParsingException;
import com.intellij.psi.impl.source.parsing.tabular.ParsingUtil;
import com.intellij.psi.impl.source.parsing.tabular.grammar.Grammar;
import com.intellij.psi.impl.source.parsing.tabular.grammar.GrammarUtil;
import com.intellij.psi.impl.source.parsing.xml.DTDMarkupParser;
import com.intellij.psi.impl.source.parsing.xml.DTDParser;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IChameleonElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.xml.IXmlElementType;
import com.intellij.util.text.CharArrayUtil;


public interface XmlElementType {
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
  IElementType HTML_DOCUMENT = new IXmlElementType("HTML_DOCUMENT");
  IElementType HTML_TAG = new IXmlElementType("HTML_TAG");
  IElementType XML_TEXT = new IXmlElementType("XML_TEXT");


  IFileElementType HTML_FILE = new IFileElementType(StdLanguages.HTML){
    public ASTNode parseContents(ASTNode chameleon) {
      final Grammar grammarByName = GrammarUtil.getGrammarByName(StdFileTypes.HTML.getName());
      final char[] chars = ((LeafElement)chameleon).textToCharArray();
      final FileViewProvider viewProvider = TreeUtil.getFileElement((TreeElement)chameleon).getPsi().getContainingFile().getViewProvider();
      return ParsingUtil.parse(grammarByName, SharedImplUtil.findCharTableByTree(chameleon), chars, viewProvider);
    }
    public boolean isParsable(CharSequence buffer, final Project project) {return true;}
  };

  IFileElementType XML_FILE = new IFileElementType(StdLanguages.XML){
    public ASTNode parseContents(ASTNode chameleon) {
      final Grammar grammarByName = GrammarUtil.getGrammarByName(StdFileTypes.XML.getName());
      final char[] chars = ((LeafElement)chameleon).textToCharArray();
      final FileViewProvider viewProvider = TreeUtil.getFileElement((TreeElement)chameleon).getPsi().getContainingFile().getViewProvider();
      return ParsingUtil.parse(grammarByName, SharedImplUtil.findCharTableByTree(chameleon), chars, viewProvider);
    }
    public boolean isParsable(CharSequence buffer, final Project project) {return true;}
  };


  IElementType XHTML_FILE = new IChameleonElementType("XML_FILE", StdLanguages.XHTML){
    public ASTNode parseContents(ASTNode chameleon) {
      final Grammar grammarByName = GrammarUtil.getGrammarByName(StdFileTypes.XHTML.getName());
      final char[] chars = ((LeafElement)chameleon).textToCharArray();
      final FileViewProvider viewProvider = TreeUtil.getFileElement((TreeElement)chameleon).getPsi().getContainingFile().getViewProvider();
      return ParsingUtil.parse(grammarByName, SharedImplUtil.findCharTableByTree(chameleon), chars, viewProvider);
    }
    public boolean isParsable(CharSequence buffer, final Project project) {return true;}
  };


  IElementType DTD_FILE = new IChameleonElementType("DTD_FILE", StdLanguages.DTD){
    public ASTNode parseContents(ASTNode chameleon) {
      final char[] chars = ((LeafElement)chameleon).textToCharArray();
      final DTDParser parser = new DTDParser();
      try {
        return parser.parse(chars, 0, chars.length, SharedImplUtil.findCharTableByTree(chameleon), SharedImplUtil.getManagerByTree(chameleon));
      }
      catch (ParsingException e) {}
      return null;
    }
    public boolean isParsable(CharSequence buffer, final Project project) {return true;}
  };

  IElementType XML_MARKUP = new IChameleonElementType("XML_MARKUP_DECL", StdLanguages.XML){
    public ASTNode parseContents(ASTNode chameleon) {
      final char[] chars = ((LeafElement)chameleon).textToCharArray();
      final DTDMarkupParser parser = new DTDMarkupParser();
      try {
        return parser.parse(chars, 0, chars.length, SharedImplUtil.findCharTableByTree(chameleon), SharedImplUtil.getManagerByTree(chameleon));
      }
      catch (ParsingException e) {}
      return null;
    }

    public boolean isParsable(CharSequence buffer, final Project project) {
      final OldXmlLexer oldXmlLexer = new OldXmlLexer();
      final char[] chars = CharArrayUtil.fromSequence(buffer);

      oldXmlLexer.start(chars, 0, buffer.length());
      while(oldXmlLexer.getTokenType() != null && oldXmlLexer.getTokenEnd() != buffer.length()){
        if(oldXmlLexer.getTokenType() == XmlTokenType.XML_MARKUP_END) return false;
        oldXmlLexer.advance();
      }
      return true;
    }
  };
}
