// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.Navigatable;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.xml.DtdParsing;
import com.intellij.psi.tree.xml.IXmlLeafElementType;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.xml.XmlElementType.XML_ELEMENT_DECL;
import static com.intellij.psi.xml.XmlElementType.XML_ENTITY_DECL;

public class XmlEntityDeclImpl extends XmlElementImpl implements XmlEntityDecl {
  public XmlEntityDeclImpl() {
    super(XML_ENTITY_DECL);
  }

  @Override
  public PsiElement getNameElement() {
    for (ASTNode e = getFirstChildNode(); e != null; e = e.getTreeNext()) {
      if (e instanceof XmlTokenImpl xmlToken) {

        if (xmlToken.getTokenType() == XmlTokenType.XML_NAME) return xmlToken;
      }
    }

    return null;
  }

  @Override
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

  @Override
  public String getName() {
    PsiElement nameElement = getNameElement();
    return nameElement != null ? nameElement.getText() : "";
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    final PsiElement nameElement = getNameElement();

    if (nameElement != null) {
      return ElementManipulators.handleContentChange(
        nameElement,
        new TextRange(0, nameElement.getTextLength()),
        name
      );
    }
    return null;
  }

  @Override
  public PsiElement parse(PsiFile baseFile, XmlEntityContextType contextType, final XmlEntityRef originalElement) {
    PsiElement dep = DEPENDING_ELEMENT.get(getParent());
    PsiElement dependsOnElement = getValueElement(dep instanceof PsiFile ? (PsiFile)dep : baseFile);
    String value = null;
    if (dependsOnElement instanceof XmlAttributeValue attributeValue) {
      value = attributeValue.getValue();
    }
    else if (dependsOnElement instanceof PsiFile file) {
      value = file.getText();
    }

    if (value == null) return null;

    DtdParsing dtdParsing = new DtdParsing(value, XML_ELEMENT_DECL, contextType, baseFile);
    PsiElement generated = dtdParsing.parse().getPsi().getFirstChild();
    if (contextType == XmlEntityContextType.ELEMENT_CONTENT_SPEC && generated instanceof XmlElementContentSpec) {
      generated = generated.getFirstChild();
    }
    setDependsOnElement(generated, dependsOnElement);
    return setOriginalElement(generated, originalElement);
  }

  private static PsiElement setDependsOnElement(PsiElement generated, PsiElement dependsOnElement) {
    PsiElement e = generated;
    while (e != null) {
      e.putUserData(DEPENDING_ELEMENT, dependsOnElement);
      e = e.getNextSibling();
    }
    return generated;
  }

  private static PsiElement setOriginalElement(PsiElement element, PsiElement valueElement) {
    PsiElement e = element;
    while (e != null) {
      e.putUserData(INCLUDING_ELEMENT, (XmlElement)valueElement);
      e = e.getNextSibling();
    }
    return element;
  }

  private @Nullable PsiElement getValueElement(PsiFile baseFile) {
    final XmlAttributeValue attributeValue = getValueElement();
    if (isInternalReference()) return attributeValue;

    if (attributeValue != null) {
      final String value = attributeValue.getValue();
      XmlFile xmlFile = XmlUtil.findNamespaceByLocation(baseFile, value);
      if (xmlFile != null) {
        return xmlFile;
      }

      final int i = XmlUtil.getPrefixLength(value);
      if (i > 0) {
        return XmlUtil.findNamespaceByLocation(baseFile, value.substring(i));
      }
    }

    return null;
  }

  @Override
  public boolean isInternalReference() {
    for (ASTNode e = getFirstChildNode(); e != null; e = e.getTreeNext()) {
      if (e.getElementType() instanceof IXmlLeafElementType) {
        XmlToken token = (XmlToken)SourceTreeToPsiMap.treeElementToPsi(e);
        if (token.getTokenType() == XmlTokenType.XML_DOCTYPE_PUBLIC ||
            token.getTokenType() == XmlTokenType.XML_DOCTYPE_SYSTEM) {
          return false;
        }
      }
    }

    return true;
  }

  @Override
  public @NotNull PsiElement getNavigationElement() {
    return getNameElement();
  }

  @Override
  public int getTextOffset() {
    final PsiElement name = getNameElement();
    return name != null ? name.getTextOffset() : super.getTextOffset();
  }

  @Override
  public boolean canNavigate() {
    if (isPhysical()) return super.canNavigate();
    final PsiNamedElement psiNamedElement = XmlUtil.findRealNamedElement(this);
    return psiNamedElement != null;
  }

  @Override
  public void navigate(final boolean requestFocus) {
    if (!isPhysical()) {
      ((Navigatable)XmlUtil.findRealNamedElement(this)).navigate(requestFocus);
      return;
    }
    super.navigate(requestFocus);
  }
}
