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
  @Nullable
  private final TagNameReference myTagNameReference;

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
  public boolean canResolveTo(Class<? extends PsiElement> elementClass) {
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

  @Nullable
  public static SchemaPrefix resolvePrefix(PsiElement element, String name) {
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

  @Nullable
  public TagNameReference getTagNameReference() {
    return myTagNameReference;
  }
}
