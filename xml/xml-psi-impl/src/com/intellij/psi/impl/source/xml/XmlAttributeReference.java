/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.XmlAttributeDescriptorEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlAttributeReference implements PsiReference {
  private final NullableLazyValue<XmlAttributeDescriptor> myDescriptor = new NullableLazyValue<XmlAttributeDescriptor>() {
    protected XmlAttributeDescriptor compute() {
      XmlTag parent = myAttribute.getParent();
      final XmlElementDescriptor descr = parent.getDescriptor();
      if (descr != null) {
        return descr.getAttributeDescriptor(myAttribute);
      }
      return null;
    }
  };
  private final XmlAttributeImpl myAttribute;

  public XmlAttributeReference(XmlAttributeImpl attribute) {
    myAttribute = attribute;
  }

  public XmlAttribute getElement() {
    return myAttribute;
  }

  public TextRange getRangeInElement() {
    final int parentOffset = myAttribute.getNameElement().getStartOffsetInParent();
    int nsLen = myAttribute.getNamespacePrefix().length();
    nsLen += nsLen > 0 && !myAttribute.getRealLocalName().isEmpty() ? 1 : -nsLen;
    return new TextRange(parentOffset + nsLen, parentOffset + myAttribute.getNameElement().getTextLength());
  }

  public PsiElement resolve() {
    final XmlAttributeDescriptor descriptor = getDescriptor();
    return descriptor != null ? descriptor.getDeclaration() : null;
  }

  @NotNull
  public String getCanonicalText() {
    return myAttribute.getName();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    String newName = newElementName;
    if (getDescriptor() instanceof XmlAttributeDescriptorEx) {
      final XmlAttributeDescriptorEx xmlAttributeDescriptorEx = (XmlAttributeDescriptorEx)getDescriptor();
      final String s = xmlAttributeDescriptorEx.handleTargetRename(newElementName);
      if (s != null) {
        final String prefix = myAttribute.getNamespacePrefix();
        newName = StringUtil.isEmpty(prefix) ? s : prefix + ":" + s;
      }
    }
    return myAttribute.setName(newName);
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiMetaOwner) {
      final PsiMetaOwner owner = (PsiMetaOwner)element;
      if (owner.getMetaData() instanceof XmlElementDescriptor) {
        myAttribute.setName(owner.getMetaData().getName());
      }
    }
    throw new IncorrectOperationException("Cant bind to not a xml element definition!");
  }

  public boolean isReferenceTo(PsiElement element) {
    return myAttribute.getManager().areElementsEquivalent(element, resolve());
  }

  @NotNull
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;  // moved to XmlAttributeReferenceCompletionProvider
  }

  public boolean isSoft() {
    return getDescriptor() == null;
  }

  @Nullable
  private XmlAttributeDescriptor getDescriptor() {
    return myDescriptor.getValue();
  }
}
