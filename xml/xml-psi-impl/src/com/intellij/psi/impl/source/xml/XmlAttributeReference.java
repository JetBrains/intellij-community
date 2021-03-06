// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.XmlAttributeDescriptorEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlAttributeReference implements PsiPolyVariantReference {
  private final NullableLazyValue<XmlAttributeDescriptor> myDescriptor = new NullableLazyValue<>() {
    @Override
    protected XmlAttributeDescriptor compute() {
      return myAttribute.getDescriptor();
    }
  };
  private final XmlAttribute myAttribute;

  public XmlAttributeReference(@NotNull XmlAttribute attribute) {
    myAttribute = attribute;
  }

  @NotNull
  @Override
  public XmlAttribute getElement() {
    return myAttribute;
  }

  @NotNull
  @Override
  public TextRange getRangeInElement() {
    final int parentOffset = myAttribute.getNameElement().getStartOffsetInParent();
    int nsLen = myAttribute.getNamespacePrefix().length();
    String realName = XmlAttributeImpl.getRealName(myAttribute);
    nsLen += nsLen > 0 && !realName.isEmpty() ? 1 : -nsLen;
    return new TextRange(parentOffset + nsLen, parentOffset + myAttribute.getNameElement().getTextLength());
  }

  @Override
  public PsiElement resolve() {
    final XmlAttributeDescriptor descriptor = getDescriptor();
    return descriptor != null ? descriptor.getDeclaration() : null;
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    return myAttribute.getName();
  }

  @Override
  public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    final XmlAttributeDescriptor descriptor = getDescriptor();
    return descriptor != null ? PsiElementResolveResult.createResults(descriptor.getDeclarations())
                              : ResolveResult.EMPTY_ARRAY;
  }
  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
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

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiMetaOwner) {
      final PsiMetaOwner owner = (PsiMetaOwner)element;
      if (owner.getMetaData() instanceof XmlElementDescriptor) {
        myAttribute.setName(owner.getMetaData().getName());
      }
    }
    throw new IncorrectOperationException("Cant bind to not a xml element definition!");
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    PsiManager manager = getElement().getManager();
    return ContainerUtil.exists(multiResolve(false), result ->
      result.isValidResult() && manager.areElementsEquivalent(element, result.getElement()));
  }

  @Override
  public Object @NotNull [] getVariants() {
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;  // moved to XmlAttributeReferenceCompletionProvider
  }

  @Override
  public boolean isSoft() {
    return getDescriptor() == null;
  }

  @Nullable
  protected XmlAttributeDescriptor getDescriptor() {
    return myDescriptor.getValue();
  }
}
