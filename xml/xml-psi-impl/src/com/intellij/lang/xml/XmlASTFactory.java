// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.lang.xml;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.platform.syntax.psi.ExtraWhitespaces;
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
import static com.intellij.psi.xml.XmlTokenType.XML_REAL_WHITE_SPACE;

public class XmlASTFactory extends ASTFactory {
  @Override
  public CompositeElement createComposite(final @NotNull IElementType type) {
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
      return new CompositePsiElement(XML_CDATA) {
      };
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
  public LeafElement createLeaf(final @NotNull IElementType type, @NotNull CharSequence text) {
    if (type instanceof IXmlLeafElementType) {
      if (type == XML_REAL_WHITE_SPACE) {
        return new PsiWhiteSpaceImpl(text);
      }
      return new XmlTokenImpl(type, text);
    }

    return null;
  }

  static {
    /*
      XML has a special whitespace kind `XmlTokenType.XML_REAL_WHITE_SPACE`.
      On AST construction, a leaf with this token type gets replaced with a plain PsiWhiteSpaceImpl
      (which has the plain WHITE_SPACE token type), see `XmlASTFactory.createLeaf`, so XML_REAL_WHITE_SPACE exists during parsing only.

      But when we want to reparse the file, we end up in a situation when a new not-yet-built tree is
      compared with the existing tree. And here, we need to deal with inconsistency in token types between XML_REAL_WHITE_SPACE in the new tree and WHITE_SPACE in the old tree.

      To overcome this situation, this hack is introduced
     */
    PsiBuilderImpl.registerWhitespaceToken(XML_REAL_WHITE_SPACE);
    ExtraWhitespaces.registerExtraWhitespace(XML_REAL_WHITE_SPACE);
  }
}
