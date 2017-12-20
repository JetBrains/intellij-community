/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.lang.xml;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.psi.impl.source.html.HtmlDocumentImpl;
import com.intellij.psi.impl.source.html.HtmlTagImpl;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.xml.*;
import com.intellij.psi.templateLanguages.ITemplateDataElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.tree.xml.IXmlLeafElementType;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.xml.XmlElementType.*;

public class XmlASTFactory extends ASTFactory {
  @Override
  public CompositeElement createComposite(@NotNull final IElementType type) {
    if (type == XML_TAG) {
      return new XmlTagImpl();
    }
    if (type == XML_CONDITIONAL_SECTION) {
      return new XmlConditionalSectionImpl();
    }
    if (type == HTML_TAG) {
      return new HtmlTagImpl();
    }
    if (type == XML_TEXT) {
      return new XmlTextImpl();
    }
    if (type == XML_PROCESSING_INSTRUCTION) {
      return new XmlProcessingInstructionImpl();
    }
    if (type == XML_DOCUMENT) {
      return new XmlDocumentImpl();
    }
    if (type == HTML_DOCUMENT) {
      return new HtmlDocumentImpl();
    }
    if (type == XML_PROLOG) {
      return new XmlPrologImpl();
    }
    if (type == XML_DECL) {
      return new XmlDeclImpl();
    }
    if (type == XML_ATTRIBUTE) {
      return new XmlAttributeImpl();
    }
    if (type == XML_ATTRIBUTE_VALUE) {
      return new XmlAttributeValueImpl();
    }
    if (type == XML_COMMENT) {
      return new XmlCommentImpl();
    }
    if (type == XML_DOCTYPE) {
      return new XmlDoctypeImpl();
    }
    if (type == XML_MARKUP_DECL) {
      return new XmlMarkupDeclImpl();
    }
    if (type == XML_ELEMENT_DECL) {
      return new XmlElementDeclImpl();
    }
    if (type == XML_ENTITY_DECL) {
      return new XmlEntityDeclImpl();
    }
    if (type == XML_ATTLIST_DECL) {
      return new XmlAttlistDeclImpl();
    }
    if (type == XML_ATTRIBUTE_DECL) {
      return new XmlAttributeDeclImpl();
    }
    if (type == XML_NOTATION_DECL) {
      return new XmlNotationDeclImpl();
    }
    if (type == XML_ELEMENT_CONTENT_SPEC) {
      return new XmlElementContentSpecImpl();
    }
    if (type == XML_ELEMENT_CONTENT_GROUP) {
      return new XmlElementContentGroupImpl();
    }
    if (type == XML_ENTITY_REF) {
      return new XmlEntityRefImpl();
    }
    if (type == XML_ENUMERATED_TYPE) {
      return new XmlEnumeratedTypeImpl();
    }
    if (type == XML_CDATA) {
      return new CompositePsiElement(XML_CDATA) {};
    }
    if (type instanceof ITemplateDataElementType) {
      return new XmlFileElement(type, null);
    }

    return null;
  }

  @Override
  public LazyParseableElement createLazy(@NotNull ILazyParseableElementType type, CharSequence text) {
    if (type == XML_FILE) {
      return new XmlFileElement(type, text);
    }
    if (type == DTD_FILE) {
      return new XmlFileElement(type, text);
    }
    if (type == XHTML_FILE) {
      return new XmlFileElement(type, text);
    }
    if (type == HTML_FILE) {
      return new HtmlFileElement(text);
    }
    if (type instanceof ITemplateDataElementType) {
      return new XmlFileElement(type, text);
    }
    return null;
  }

  @Override
  public LeafElement createLeaf(@NotNull final IElementType type, @NotNull CharSequence text) {
    if (type instanceof IXmlLeafElementType) {
      if (type == XML_REAL_WHITE_SPACE) {
        return new PsiWhiteSpaceImpl(text);
      }
      return new XmlTokenImpl(type, text);
    }

    return null;
  }

  static {
    PsiBuilderImpl.registerWhitespaceToken(XML_REAL_WHITE_SPACE);    
  }
}
