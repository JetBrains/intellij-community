/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.lang.*;
import com.intellij.lang.dtd.DTDLanguage;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.html.HTMLParser;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.parsing.xml.DtdParsing;
import com.intellij.psi.tree.*;
import com.intellij.psi.tree.xml.IXmlElementType;
import com.intellij.util.CharTable;
import com.intellij.util.diff.FlyweightCapableTreeStructure;


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

  //todo: move to html
  IElementType HTML_DOCUMENT = new IXmlElementType("HTML_DOCUMENT");
  IElementType HTML_TAG = new IXmlElementType("HTML_TAG");
  IFileElementType HTML_FILE = new IStubFileElementType(HTMLLanguage.INSTANCE);
  IElementType HTML_EMBEDDED_CONTENT = new EmbeddedHtmlContentElementType();

  IElementType XML_TEXT = new XmlTextElementType();

  IFileElementType XML_FILE = new IFileElementType(XMLLanguage.INSTANCE);
  IElementType XHTML_FILE = new IFileElementType(XHTMLLanguage.INSTANCE);


  IFileElementType DTD_FILE = new IFileElementType("DTD_FILE", DTDLanguage.INSTANCE);

  IElementType XML_MARKUP_DECL = new CustomParsingType("XML_MARKUP_DECL", XMLLanguage.INSTANCE){
    @Override
    public ASTNode parse(CharSequence text, CharTable table) {
      return new DtdParsing(text, XML_MARKUP_DECL, DtdParsing.TYPE_FOR_MARKUP_DECL, null).parse();
    }
  };

  class EmbeddedHtmlContentElementType extends ILazyParseableElementType implements ILightLazyParseableElementType {
    public EmbeddedHtmlContentElementType() {
      super("HTML_EMBEDDED_CONTENT", HTMLLanguage.INSTANCE);
    }

    @Override
    public FlyweightCapableTreeStructure<LighterASTNode> parseContents(LighterLazyParseableNode chameleon) {
      final PsiFile file = chameleon.getContainingFile();
      assert file != null : chameleon;

      final PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(file.getProject(), chameleon);
      HTMLParser.parseWithoutBuildingTree(HTML_FILE, builder);
      return builder.getLightTree();
    }
  }
}
