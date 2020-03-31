// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.xml.stub.XmlAttributeStub;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public class XmlStubBasedAttributeBase<StubT extends XmlAttributeStub<?>>
  extends XmlStubBasedElement<StubT>
  implements XmlAttribute, HintedReferenceHost, StubBasedPsiElement<StubT> {

  //cannot be final because of clone implementation
  @Nullable
  private volatile XmlAttributeDelegate myImpl;

  public XmlStubBasedAttributeBase(@NotNull StubT stub,
                                   @NotNull IStubElementType<? extends StubT, ? extends XmlAttribute> nodeType) {
    super(stub, nodeType);
  }

  public XmlStubBasedAttributeBase(@NotNull ASTNode node) {
    super(node);
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
    return new XmlStubBasedAttributeBaseDelegate();
  }

  @Override
  public XmlAttributeValue getValueElement() {
    return (XmlAttributeValue)XmlChildRole.ATTRIBUTE_VALUE_FINDER.findChild(this.getNode());
  }

  @Override
  public void setValue(@NotNull String valueText) throws IncorrectOperationException {
    getImpl().setValue(valueText);
  }

  @Override
  public XmlElement getNameElement() {
    return (XmlElement)XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(this.getNode());
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
  public PsiElement getContext() {
    XmlAttributeStub<?> stub = getStub();
    if (stub != null) {
      if (!(stub instanceof PsiFileStub)) {
        return stub.getParentStub().getPsi();
      }
    }
    return super.getParent();
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
  public void subtreeChanged() {
    super.subtreeChanged();
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
  public boolean shouldAskParentForReferences(@NotNull PsiReferenceService.Hints hints) {
    return false;
  }

  @Override
  @Nullable
  public XmlAttributeDescriptor getDescriptor() {
    return getImpl().getDescriptor();
  }

  protected class XmlStubBasedAttributeBaseDelegate extends XmlAttributeDelegate {

    public XmlStubBasedAttributeBaseDelegate() {
      super(XmlStubBasedAttributeBase.this);
    }
  }
}
