// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.completion.CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED;

public class XmlAttributeImpl extends XmlElementImpl implements XmlAttribute, HintedReferenceHost {
  private static final Logger LOG = Logger.getInstance(XmlAttributeImpl.class);

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  private final int myHC = ourHC++;

  //cannot be final because of clone implementation
  private volatile @Nullable XmlAttributeDelegate myImpl;

  @Override
  public final int hashCode() {
    return myHC;
  }

  public XmlAttributeImpl() {
    super(XmlElementType.XML_ATTRIBUTE);
  }

  protected XmlAttributeImpl(@NotNull IElementType elementType) {
    super(elementType);
  }

  private @NotNull XmlAttributeDelegate getImpl() {
    XmlAttributeDelegate impl = myImpl;
    if (impl != null) return impl;
    impl = createDelegate();
    myImpl = impl;

    return impl;
  }

  protected @NotNull XmlAttributeDelegate createDelegate() {
    return new XmlAttributeImplDelegate();
  }

  @Override
  public XmlAttributeValue getValueElement() {
    return (XmlAttributeValue)XmlChildRole.ATTRIBUTE_VALUE_FINDER.findChild(this);
  }

  @Override
  public void setValue(@NotNull String valueText) throws IncorrectOperationException {
    getImpl().setValue(valueText);
  }

  @Override
  public @Nullable XmlElement getNameElement() {
    ASTNode child = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(this);
    return child == null ? null : (XmlElement)child.getPsi();
  }

  @Override
  public @NotNull String getNamespace() {
    return getImpl().getNamespace();
  }

  @Override
  public @NonNls @NotNull String getNamespacePrefix() {
    return XmlUtil.findPrefixByQualifiedName(getName());
  }

  @Override
  public @Nullable XmlTag getParent() {
    final PsiElement parentTag = super.getParent();
    return parentTag instanceof XmlTag ? (XmlTag)parentTag : null; // Invalid elements might belong to DummyHolder instead.
  }

  @Override
  public @NotNull String getLocalName() {
    return XmlUtil.findLocalNameByQualifiedName(getName());
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlAttribute(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String getValue() {
    final XmlAttributeValue valueElement = getValueElement();
    return valueElement != null ? valueElement.getValue() : null;
  }

  @Override
  public @Nullable String getDisplayValue() {
    final XmlAttributeDelegate.VolatileState state = getImpl().getFreshState();
    return state == null ? null : state.myDisplayText;
  }

  @Override
  public int physicalToDisplay(int physicalIndex) {
    return getImpl().physicalToDisplay(physicalIndex);
  }

  @Override
  public int displayToPhysical(int displayIndex) {
    return getImpl().displayToPhysical(displayIndex);
  }

  @Override
  public @NotNull TextRange getValueTextRange() {
    final XmlAttributeDelegate.VolatileState state = getImpl().getFreshState();
    return state == null ? TextRange.EMPTY_RANGE : state.myValueTextRange;
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    myImpl = null;
  }

  @Override
  public @NotNull String getName() {
    XmlElement element = getNameElement();
    return element != null ? element.getText() : "";
  }

  @Override
  public boolean isNamespaceDeclaration() {
    final @NonNls String name = getName();
    return name.startsWith("xmlns:") || name.equals("xmlns");
  }

  @Override
  public @NotNull PsiElement setName(final @NotNull String nameText) throws IncorrectOperationException {
    return getImpl().setName(nameText);
  }

  @Override
  public PsiReference getReference() {
    return ArrayUtil.getFirstElement(getReferences(PsiReferenceService.Hints.NO_HINTS));
  }

  @Override
  public boolean shouldAskParentForReferences(@NotNull PsiReferenceService.Hints hints) {
    return false;
  }

  /**
   * @deprecated use {@link #getReferences(PsiReferenceService.Hints)} instead of calling or overriding this method.
   */
  @Deprecated
  @Override
  public final PsiReference @NotNull [] getReferences() {
    return getReferences(PsiReferenceService.Hints.NO_HINTS);
  }

  @Override
  public PsiReference @NotNull [] getReferences(@NotNull PsiReferenceService.Hints hints) {
    return getImpl().getDefaultReferences(hints);
  }

  @Override
  public @Nullable XmlAttributeDescriptor getDescriptor() {
    return getImpl().getDescriptor();
  }

  public static @NotNull String getRealName(@NotNull XmlAttribute attribute) {
    final String name = attribute.getLocalName();
    return name.endsWith(DUMMY_IDENTIFIER_TRIMMED) ? name.substring(0, name.length() - DUMMY_IDENTIFIER_TRIMMED.length()) : name;
  }

  protected class XmlAttributeImplDelegate extends XmlAttributeDelegate {

    public XmlAttributeImplDelegate() {
      super(XmlAttributeImpl.this);
    }
  }
}
