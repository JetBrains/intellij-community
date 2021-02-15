// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @Nullable
  private volatile XmlAttributeDelegate myImpl;

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

  @NotNull
  private XmlAttributeDelegate getImpl() {
    XmlAttributeDelegate impl = myImpl;
    if (impl != null) return impl;
    impl = createDelegate();
    myImpl = impl;

    return impl;
  }

  @NotNull
  protected XmlAttributeDelegate createDelegate() {
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
  public XmlElement getNameElement() {
    ASTNode child = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(this);
    return child == null ? null : (XmlElement)child.getPsi();
  }

  @Override
  @NotNull
  public String getNamespace() {
    return getImpl().getNamespace();
  }

  @Override
  @NonNls
  @NotNull
  public String getNamespacePrefix() {
    return XmlUtil.findPrefixByQualifiedName(getName());
  }

  @Override
  public XmlTag getParent() {
    final PsiElement parentTag = super.getParent();
    return parentTag instanceof XmlTag ? (XmlTag)parentTag : null; // Invalid elements might belong to DummyHolder instead.
  }

  @Override
  @NotNull
  public String getLocalName() {
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
  @Nullable
  public String getDisplayValue() {
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

  @NotNull
  @Override
  public TextRange getValueTextRange() {
    final XmlAttributeDelegate.VolatileState state = getImpl().getFreshState();
    return state == null ? TextRange.EMPTY_RANGE : state.myValueTextRange;
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    myImpl = null;
  }

  @Override
  @NotNull
  public String getName() {
    XmlElement element = getNameElement();
    return element != null ? element.getText() : "";
  }

  @Override
  public boolean isNamespaceDeclaration() {
    @NonNls final String name = getName();
    return name.startsWith("xmlns:") || name.equals("xmlns");
  }

  @Override
  @NotNull
  public PsiElement setName(@NotNull final String nameText) throws IncorrectOperationException {
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
  @Nullable
  public XmlAttributeDescriptor getDescriptor() {
    return getImpl().getDescriptor();
  }

  @NotNull
  public static String getRealName(@NotNull XmlAttribute attribute) {
    final String name = attribute.getLocalName();
    return name.endsWith(DUMMY_IDENTIFIER_TRIMMED) ? name.substring(0, name.length() - DUMMY_IDENTIFIER_TRIMMED.length()) : name;
  }

  protected class XmlAttributeImplDelegate extends XmlAttributeDelegate {

    public XmlAttributeImplDelegate() {
      super(XmlAttributeImpl.this);
    }
  }
}
