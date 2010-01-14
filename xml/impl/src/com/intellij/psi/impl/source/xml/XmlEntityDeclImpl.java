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
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer._OldXmlLexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.Navigatable;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.xml.OldXmlParsing;
import com.intellij.psi.impl.source.parsing.xml.XmlParsingContext;
import com.intellij.psi.impl.source.parsing.xml.XmlPsiLexer;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.URIReferenceProvider;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.tree.xml.IXmlLeafElementType;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author mike
 */
public class XmlEntityDeclImpl extends XmlElementImpl implements XmlEntityDecl, XmlElementType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlEntityDeclImpl");

  public XmlEntityDeclImpl() {
    super(XML_ENTITY_DECL);
  }

  public PsiElement getNameElement() {
    for (ASTNode e = getFirstChildNode(); e != null; e = e.getTreeNext()) {
      if (e instanceof XmlTokenImpl) {
        XmlTokenImpl xmlToken = (XmlTokenImpl)e;

        if (xmlToken.getTokenType() == XmlTokenType.XML_NAME) return xmlToken;
      }
    }

    return null;
  }

  public XmlAttributeValue getValueElement() {
    if (isInternalReference()) {
      for (ASTNode e = getFirstChildNode(); e != null; e = e.getTreeNext()) {
        if (e.getElementType() == XmlElementType.XML_ATTRIBUTE_VALUE) {
          return (XmlAttributeValue)SourceTreeToPsiMap.treeElementToPsi(e);
        }
      }
    }
    else {
      for (ASTNode e = getLastChildNode(); e != null; e = e.getTreePrev()) {
        if (e.getElementType() == XmlElementType.XML_ATTRIBUTE_VALUE) {
          return (XmlAttributeValue)SourceTreeToPsiMap.treeElementToPsi(e);
        }
      }
    }

    return null;
  }

  public String getName() {
    PsiElement nameElement = getNameElement();
    return nameElement != null ? nameElement.getText() : "";
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    final PsiElement nameElement = getNameElement();

    if (nameElement != null) {
      return ElementManipulators.getManipulator(nameElement).handleContentChange(
        nameElement,
        new TextRange(0,nameElement.getTextLength()),
        name
      );
    }
    return null;
  }

  public PsiElement parse(PsiFile baseFile, int context, final XmlEntityRef originalElement) {
    PsiElement dependsOnElement = getValueElement(baseFile);
    String value = null;
    if (dependsOnElement instanceof XmlAttributeValue) {
      XmlAttributeValue attributeValue = (XmlAttributeValue)dependsOnElement;
      value = attributeValue.getValue();
    }
    else if (dependsOnElement instanceof PsiFile) {
      PsiFile file = (PsiFile)dependsOnElement;
      value = file.getText();
    }

    if (value == null) return null;
    final FileElement holderElement = DummyHolderFactory.createHolder(originalElement.getManager(), originalElement).getTreeElement();

    Lexer lexer = getLexer(context, value);
    final XmlParsingContext parsingContext = new XmlParsingContext(holderElement.getCharTable());
    final CompositeElement element = ASTFactory.composite(XML_ELEMENT_DECL);
    holderElement.rawAddChildren(element);

    switch (context) {
      default :
        LOG.assertTrue(false, "Entity: " + getName() + " context: " + context);
        return null;

      case CONTEXT_ELEMENT_CONTENT_SPEC:
        {
          parsingContext.getXmlParsing().parseElementContentSpec(element, lexer);
          final PsiElement generated = SourceTreeToPsiMap.treeElementToPsi(element).getFirstChild().getFirstChild();
          setDependsOnElement(generated, dependsOnElement);
          return setOriginalElement(generated, originalElement);
        }

      case CONTEXT_ATTRIBUTE_SPEC:
        {
          parsingContext.getXmlParsing().parseAttributeContentSpec(element, lexer);
          final PsiElement generated = SourceTreeToPsiMap.treeElementToPsi(element).getFirstChild();
          setDependsOnElement(generated, dependsOnElement);
          return setOriginalElement(generated, originalElement);
        }

      case CONTEXT_ATTLIST_SPEC:
        {
          parsingContext.getXmlParsing().parseAttlistContent(element, lexer);
          final PsiElement generated = SourceTreeToPsiMap.treeElementToPsi(element).getFirstChild();
          setDependsOnElement(generated, dependsOnElement);
          return setOriginalElement(generated, originalElement);
        }

      case CONTEXT_ATTR_VALUE:
        {
          parsingContext.getXmlParsing().parseAttrValue(element, lexer);
          final PsiElement generated = SourceTreeToPsiMap.treeElementToPsi(element).getFirstChild();
          setDependsOnElement(generated, dependsOnElement);
          return setOriginalElement(generated, originalElement);
        }

      case CONTEXT_ENTITY_DECL_CONTENT:
        {
          parsingContext.getXmlParsing().parseEntityDeclContent(element, lexer);
          final PsiElement generated = SourceTreeToPsiMap.treeElementToPsi(element).getFirstChild();
          setDependsOnElement(generated, dependsOnElement);
          return setOriginalElement(generated, originalElement);
        }

      case CONTEXT_GENERIC_XML:
        {

          Set<String> names = new HashSet<String>();
          {
            // calculating parent names
            PsiElement parent = originalElement;
            while(parent != null){
              if(parent instanceof XmlTag){
                names.add(((XmlTag)parent).getName());
              }
              parent = parent.getParent();
            }
          }
          parsingContext.getXmlParsing().parseGenericXml(lexer, element, names);
          final PsiElement generated = SourceTreeToPsiMap.treeElementToPsi(element).getFirstChild();
          setDependsOnElement(generated, dependsOnElement);
          return setOriginalElement(generated, originalElement);
        }

      case CONTEXT_ENUMERATED_TYPE:
        {
          parsingContext.getXmlParsing().parseEnumeratedTypeContent(element, lexer);
          final PsiElement generated = SourceTreeToPsiMap.treeElementToPsi(element).getFirstChild();
          setDependsOnElement(generated, dependsOnElement);
          return setOriginalElement(generated, originalElement);
        }
    }
  }

  private PsiElement setDependsOnElement(PsiElement generated, PsiElement dependsOnElement) {
    PsiElement e = generated;
    while (e != null) {
      e.putUserData(XmlElement.DEPENDING_ELEMENT, dependsOnElement);
      e = e.getNextSibling();
    }
    return generated;
  }

  private PsiElement setOriginalElement(PsiElement element, PsiElement valueElement) {
    PsiElement e = element;
    while (e != null) {
      e.putUserData(XmlElement.INCLUDING_ELEMENT, (XmlElement)valueElement);
      e = e.getNextSibling();
    }
    return element;
  }

  public static Lexer getLexer(int context, CharSequence buffer) {
    Lexer lexer = new XmlPsiLexer();
    FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(OldXmlParsing.XML_WHITE_SPACE_OR_COMMENT_BIT_SET));
    short state = 0;

    switch (context) {
      case CONTEXT_ELEMENT_CONTENT_SPEC:
      case CONTEXT_ATTRIBUTE_SPEC:
      case CONTEXT_ATTLIST_SPEC:
      case CONTEXT_ENUMERATED_TYPE:
      case CONTEXT_ENTITY_DECL_CONTENT:
        {
          state = _OldXmlLexer.DOCTYPE_MARKUP;
          break;
        }

      case CONTEXT_ATTR_VALUE:
      case CONTEXT_GENERIC_XML: {
        break;
      }


      default: LOG.error("context: " + context);
    }

    filterLexer.start(buffer, 0, buffer.length(), state);
    return filterLexer;
  }

  @Nullable
  private PsiElement getValueElement(PsiFile baseFile) {
    final XmlAttributeValue attributeValue = getValueElement();
    if (isInternalReference()) return attributeValue;

    if (attributeValue != null) {
      final String value = attributeValue.getValue();
      if (value != null) {
        XmlFile xmlFile = XmlUtil.findNamespaceByLocation(baseFile, value);
        if (xmlFile != null) {
          return xmlFile;
        }

        final int i = URIReferenceProvider.getPrefixLength(value);
        if (i > 0) {
          return XmlUtil.findNamespaceByLocation(baseFile, value.substring(i));
        }
      }
    }

    return null;
  }

  public boolean isInternalReference() {
    for (ASTNode e = getFirstChildNode(); e != null; e = e.getTreeNext()) {
      if (e.getElementType() instanceof IXmlLeafElementType) {
        XmlToken token = (XmlToken)SourceTreeToPsiMap.treeElementToPsi(e);
        if (token.getTokenType() == XmlTokenType.XML_DOCTYPE_PUBLIC || token.getTokenType() == XmlTokenType.XML_DOCTYPE_SYSTEM) {
          return false;
        }
      }
    }

    return true;
  }

  @NotNull
  public PsiElement getNavigationElement() {
    return getNameElement();
  }

  public int getTextOffset() {
    final PsiElement name = getNameElement();
    return name != null ? name.getTextOffset() : super.getTextOffset();
  }

  public boolean canNavigate() {
    if (isPhysical()) return super.canNavigate();
    final PsiNamedElement psiNamedElement = XmlUtil.findRealNamedElement(this);
    return psiNamedElement != null;
  }

  public void navigate(final boolean requestFocus) {
    if (!isPhysical()) {
      ((Navigatable)XmlUtil.findRealNamedElement(this)).navigate(requestFocus);
      return;
    }
    super.navigate(requestFocus);
  }
}
