// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolvingHint;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ReflectionUtil;
import com.intellij.xml.XmlExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class SchemaPrefixReference extends PsiReferenceBase<XmlElement> implements PossiblePrefixReference, ResolvingHint {

  private final SchemaPrefix myPrefix;

  private final String myName;
  private final @Nullable TagNameReference myTagNameReference;

  /**
   *
   * @param element XmlAttribute || XmlAttributeValue || XmlTag
   */
  public SchemaPrefixReference(XmlElement element, TextRange range, String name, @Nullable TagNameReference reference) {
    super(element, range);
    myName = name;
    myTagNameReference = reference;
    if (myElement instanceof XmlAttribute && (((XmlAttribute)myElement).isNamespaceDeclaration())) {
      myPrefix = new SchemaPrefix((XmlAttribute)myElement, getRangeInElement(), myName);
    }
    else if (myElement instanceof XmlAttributeValue &&
             ((XmlAttribute)myElement.getParent()).getLocalName().equals("prefix")) {
      myPrefix = SchemaPrefix.createJspPrefix((XmlAttributeValue)myElement, myName);
    }
    else {
      myPrefix = null;
    }
  }

  public String getNamespacePrefix() {
    return myName;
  }

  @Override
  public boolean canResolveTo(@NotNull Class<? extends PsiElement> elementClass) {
    return ReflectionUtil.isAssignable(XmlElement.class, elementClass);
  }

  @Override
  public SchemaPrefix resolve() {
    return myPrefix == null ? resolvePrefix(myElement, myName) : myPrefix;
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    if (!(element instanceof SchemaPrefix) || !myName.equals(((SchemaPrefix)element).getName())) return false;
    return super.isReferenceTo(element);
  }

  @Override
  public PsiElement handleElementRename(@NotNull String name) throws IncorrectOperationException {
    if (myElement instanceof XmlAttribute attr) {
      return ("xmlns".equals(attr.getNamespacePrefix()))
             ? attr.setName(attr.getNamespacePrefix() + ":" + name)
             : attr.setName(name + ":" + attr.getLocalName());
    }
    else if (myElement instanceof XmlTag tag) {
      return tag.setName(name + ":" + tag.getLocalName());
    }
    return super.handleElementRename(name);
  }

  public static @Nullable SchemaPrefix resolvePrefix(PsiElement element, String name) {
    XmlExtension extension = XmlExtension.getExtension(element.getContainingFile());
    return extension.getPrefixDeclaration(PsiTreeUtil.getParentOfType(element, XmlTag.class, false), name);
  }

  @Override
  public boolean isSoft() {
    return true;
  }

  @Override
  public boolean isPrefixReference() {
    return true;
  }

  public @Nullable TagNameReference getTagNameReference() {
    return myTagNameReference;
  }
}
