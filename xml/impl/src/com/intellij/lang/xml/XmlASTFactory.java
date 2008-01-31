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
import com.intellij.psi.templateLanguages.TemplateDataElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.xml.IXmlLeafElementType;
import static com.intellij.psi.xml.XmlElementType.*;
import com.intellij.util.CharTable;

public class XmlASTFactory extends ASTFactory {
  public CompositeElement createComposite(final IElementType type) {
    CompositeElement element = null;

    if (type == XML_TAG) {
      element = new XmlTagImpl();
    } else if (type == XML_CONDITIONAL_SECTION) {
      element = new XmlConditionalSectionImpl();
    }
    else if (type == HTML_TAG) {
      element = new HtmlTagImpl();
    }
    else if (type == XML_TEXT) {
      element = new XmlTextImpl();
    }
    else if (type == XML_PROCESSING_INSTRUCTION) {
      element = new XmlProcessingInstructionImpl();
    }
    else if (type == XML_DOCUMENT) {
      element = new XmlDocumentImpl();
    } else if (type == HTML_DOCUMENT) {
      element = new HtmlDocumentImpl();
    }
    else if (type == XML_PROLOG) {
      element = new XmlPrologImpl();
    }
    else if (type == XML_DECL) {
      element = new XmlDeclImpl();
    }
    else if (type == XML_ATTRIBUTE) {
      element = new XmlAttributeImpl();
    }
    else if (type == XML_ATTRIBUTE_VALUE) {
      element = new XmlAttributeValueImpl();
    }
    else if (type == XML_COMMENT) {
      element = new XmlCommentImpl();
    }
    else if (type == XML_DOCTYPE) {
      element = new XmlDoctypeImpl();
    }
    else if (type == XML_MARKUP_DECL) {
      element = new XmlMarkupDeclImpl();
    }
    else if (type == XML_ELEMENT_DECL) {
      element = new XmlElementDeclImpl();
    }
    else if (type == XML_ENTITY_DECL) {
      element = new XmlEntityDeclImpl();
    }
    else if (type == XML_ATTLIST_DECL) {
      element = new XmlAttlistDeclImpl();
    }
    else if (type == XML_ATTRIBUTE_DECL) {
      element = new XmlAttributeDeclImpl();
    }
    else if (type == XML_NOTATION_DECL) {
      element = new XmlNotationDeclImpl();
    }
    else if (type == XML_ELEMENT_CONTENT_SPEC) {
      element = new XmlElementContentSpecImpl();
    }
    else if (type == XML_ENTITY_REF) {
      element = new XmlEntityRefImpl();
    }
    else if (type == XML_ENUMERATED_TYPE) {
      element = new XmlEnumeratedTypeImpl();
    }
    else if (type == XML_FILE) {
      element = new XmlFileElement(type);
    }
    else if (type == DTD_FILE) {
      element = new XmlFileElement(type);
    }
    else if (type == XHTML_FILE) {
      element = new XmlFileElement(type);
    }
    else if (type == HTML_FILE) {
      element = new HtmlFileElement();
    }
    else if (type == XML_CDATA) {
      element = new CompositePsiElement(XML_CDATA) {};
    }
    else if (type instanceof TemplateDataElementType) {
      element = new XmlFileElement(type);
    }

    return element;
  }

  public LeafElement createLeaf(final IElementType type, final CharSequence fileText, final int start, final int end, final CharTable table) {
    if (type instanceof IXmlLeafElementType) {
      if (type == XML_REAL_WHITE_SPACE) {
        return new PsiWhiteSpaceImpl(fileText, start, end, table);
      }
      return new XmlTokenImpl(type, fileText, start, end, table);
    }

    return null;
  }

  static {
    PsiBuilderImpl.registerWhitespaceToken(XML_REAL_WHITE_SPACE);    
  }
}