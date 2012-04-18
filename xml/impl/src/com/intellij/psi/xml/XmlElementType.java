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
package com.intellij.psi.xml;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.dtd.DTDLanguage;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.lexer.OldXmlLexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.source.parsing.xml.XmlParsingContext;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.tree.CustomParsingType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IReparseableElementType;
import com.intellij.psi.tree.xml.IXmlElementType;
import com.intellij.util.CharTable;


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
  IElementType XML_ELEMENT_CONTENT_GROUP = new IXmlElementType("XML_ELEMENT_CONTENT_GROUP");
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
  IFileElementType HTML_FILE = new IFileElementType(HTMLLanguage.INSTANCE);

  IElementType XML_TEXT = new XmlTextElementType();

  IFileElementType XML_FILE = new IFileElementType(XMLLanguage.INSTANCE);
  IElementType XHTML_FILE = new IFileElementType(XHTMLLanguage.INSTANCE);


  IElementType DTD_FILE = new IReparseableElementType("DTD_FILE", DTDLanguage.INSTANCE){
    public ASTNode parseContents(ASTNode chameleon) {
      final CharSequence chars = chameleon.getChars();
      final CharTable table = SharedImplUtil.findCharTableByTree(chameleon);
      final XmlParsingContext parsingContext = new XmlParsingContext(table);
      return parsingContext.getXmlParsing().parse(new OldXmlLexer(), chars, 0, chars.length(), SharedImplUtil.getManagerByTree(chameleon));
    }
    public boolean isParsable(CharSequence buffer, Language fileLanguage, final Project project) {return true;}
  };

  IElementType XML_MARKUP = new CustomParsingType("XML_MARKUP_DECL", XMLLanguage.INSTANCE){
    public ASTNode parse(CharSequence text, CharTable table) {
      final XmlParsingContext parsingContext = new XmlParsingContext(table);
      return parsingContext.getXmlParsing().parseMarkupDecl(text);
    }
  };

  IElementType XML_MARKUP_DECL = XmlElementType.XML_MARKUP;
}
