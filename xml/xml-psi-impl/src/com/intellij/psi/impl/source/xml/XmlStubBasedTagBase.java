// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.xml.stub.XmlTagStub;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.*;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.InclusionProvider;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

@ApiStatus.Experimental
public class XmlStubBasedTagBase<StubT extends XmlTagStub<?>>
  extends XmlStubBasedElement<StubT>
  implements XmlTag, HintedReferenceHost, StubBasedPsiElement<StubT> {

  //cannot be final because of clone implementation
  private volatile @Nullable XmlTagDelegate myImpl;
  private volatile XmlTagValue myValue;
  private volatile XmlAttribute[] myAttributes;

  XmlStubBasedTagBase(@NotNull StubT stub, @NotNull IElementType nodeType) {
    super(stub, nodeType);
  }

  XmlStubBasedTagBase(@NotNull ASTNode node) {
    super(node);
  }

  private @NotNull XmlTagDelegate getImpl() {
    XmlTagDelegate impl = myImpl;
    if (impl != null) return impl;
    impl = createDelegate();
    myImpl = impl;

    return impl;
  }

  protected XmlTagDelegate createDelegate() {
    return new XmlStubBasedTagDelegate();
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myImpl = null;
    myValue = null;
    myAttributes = null;
  }

  @Override
  public PsiElement getContext() {
    XmlTagStub<?> stub = getStub();
    if (stub != null) {
      if (!(stub instanceof PsiFileStub)) {
        return stub.getParentStub().getPsi();
      }
    }
    return super.getParent();
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
  public boolean shouldAskParentForReferences(@NotNull PsiReferenceService.Hints hints) {
    return false;
  }

  @Override
  public PsiReference @NotNull [] getReferences(@NotNull PsiReferenceService.Hints hints) {
    return getImpl().getDefaultReferences(hints);
  }

  @Override
  public XmlNSDescriptor getNSDescriptor(final String namespace, boolean strict) {
    return getImpl().getNSDescriptor(namespace, strict);
  }

  @Override
  public boolean isEmpty() {
    return XmlChildRole.CLOSING_TAG_START_FINDER.findChild(this.getNode()) == null;
  }

  @Override
  public void collapseIfEmpty() {
    getImpl().collapseIfEmpty();
  }

  @Override
  public @Nullable @NonNls String getSubTagText(@NonNls String qname) {
    final XmlTag tag = findFirstSubTag(qname);
    if (tag == null) return null;
    return tag.getValue().getText();
  }

  @Override
  public PsiReference getReference() {
    return ArrayUtil.getFirstElement(getReferences(PsiReferenceService.Hints.NO_HINTS));
  }

  @Override
  public XmlElementDescriptor getDescriptor() {
    return getImpl().getDescriptor();
  }

  @Override
  public @NotNull String getName() {
    return getImpl().getName();
  }

  @Override
  public PsiElement setName(final @NotNull String name) throws IncorrectOperationException {
    return getImpl().setName(name);
  }

  @Override
  public XmlAttribute @NotNull [] getAttributes() {
    XmlAttribute[] attributes = myAttributes;
    if (attributes == null) {
      myAttributes = attributes = getImpl().calculateAttributes();
    }
    return attributes.clone();
  }

  @Override
  public String getAttributeValue(String qname) {
    return getImpl().getAttributeValue(qname);
  }

  @Override
  public String getAttributeValue(String _name, String namespace) {
    return getImpl().getAttributeValue(_name, namespace);
  }

  @Override
  public XmlTag @NotNull [] getSubTags() {
    return getSubTags(InclusionProvider.getInstance().shouldProcessIncludesNow());
  }

  private XmlTag[] getSubTags(boolean processIncludes) {
    return getImpl().getSubTags(processIncludes);
  }


  @Override
  public XmlTag @NotNull [] findSubTags(@NotNull String name) {
    return findSubTags(name, null);
  }

  @Override
  public XmlTag @NotNull [] findSubTags(final @NotNull String name, final @Nullable String namespace) {
    return getImpl().findSubTags(name, namespace);
  }

  @Override
  public XmlTag findFirstSubTag(String name) {
    return getImpl().findFirstSubTag(name);
  }

  @Override
  public XmlAttribute getAttribute(String name, String namespace) {
    return getImpl().getAttribute(name, namespace);
  }

  @Override
  public @Nullable XmlAttribute getAttribute(String qname) {
    return getImpl().getAttribute(qname);
  }

  @Override
  public @NotNull String getNamespace() {
    return CachedValuesManager.getCachedValue(this, () ->
      Result.create(getNamespaceByPrefix(getNamespacePrefix()), PsiModificationTracker.MODIFICATION_COUNT));
  }

  @Override
  public @NotNull String getNamespacePrefix() {
    return getImpl().getNamespacePrefix(getName());
  }

  @Override
  public @NotNull String getNamespaceByPrefix(String prefix) {
    return getImpl().getNamespaceByPrefix(prefix);
  }

  @Override
  public String getPrefixByNamespace(String namespace) {
    return getImpl().getPrefixByNamespace(namespace);
  }

  @Override
  public String[] knownNamespaces() {
    return getImpl().knownNamespaces();
  }

  @Override
  public @NotNull String getLocalName() {
    return getImpl().getLocalName();
  }

  @Override
  public boolean hasNamespaceDeclarations() {
    return getImpl().hasNamespaceDeclarations();
  }

  @Override
  public @NotNull Map<String, String> getLocalNamespaceDeclarations() {
    return getImpl().getLocalNamespaceDeclarations();
  }

  @Override
  public XmlAttribute setAttribute(String qname, String value) throws IncorrectOperationException {
    return getImpl().setAttribute(qname, value);
  }

  @Override
  public XmlAttribute setAttribute(String name, String namespace, String value) throws IncorrectOperationException {
    return getImpl().setAttribute(name, namespace, value);
  }

  @Override
  public XmlTag createChildTag(String localName, String namespace, String bodyText, boolean enforceNamespacesDeep) {
    return XmlUtil.createChildTag(this, localName, namespace, bodyText, enforceNamespacesDeep, getImpl()::createTagFromText);
  }

  protected XmlTagValue createXmlTagValue() {
    return XmlTagValueImpl.createXmlTagValue(this);
  }

  @Override
  public XmlTag addSubTag(XmlTag subTag, boolean first) {
    return getImpl().addSubTag(subTag, first);
  }

  @Override
  public @NotNull XmlTagValue getValue() {
    XmlTagValue tagValue = myValue;
    if (tagValue == null) {
      myValue = tagValue = createXmlTagValue();
    }
    return tagValue;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlTag(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public IElementType getIElementType() {
    return getElementTypeImpl();
  }

  @Override
  public String toString() {
    return "XmlTag:" + getName();
  }

  @Override
  public PsiMetaData getMetaData() {
    return MetaRegistry.getMeta(this);
  }

  @Override
  public ASTNode addInternal(@NotNull ASTNode first, @NotNull ASTNode last, ASTNode anchor, Boolean beforeB) {
    if (!(first instanceof TreeElement)) return null;
    TreeElement firstAppended = null;
    boolean before = beforeB == null || beforeB.booleanValue();
    try {
      TreeElement next;
      do {
        next = ((TreeElement)first).getTreeNext();

        if (firstAppended == null) {
          firstAppended = getImpl().addInternal((TreeElement)first, anchor, before);
          anchor = firstAppended;
        }
        else {
          anchor = getImpl().addInternal((TreeElement)first, anchor, false);
        }
      }
      while (first != last && (first = next) != null);
    }
    finally {
      subtreeChanged();
    }
    return firstAppended;
  }

  @Override
  public void deleteChildInternal(final @NotNull ASTNode child) {
    getImpl().deleteChildInternal(child);
  }

  protected void deleteChildInternalSuper(final @NotNull ASTNode child) {
    super.deleteChildInternal(child);
  }

  protected TreeElement addInternalSuper(TreeElement first, ASTNode last, @Nullable ASTNode anchor, @Nullable Boolean before) {
    return (TreeElement)super.addInternal(first, last, anchor, before);
  }

  @Override
  public XmlTag getParentTag() {
    final PsiElement parent = getParentByStub();
    if (parent instanceof XmlTag) return (XmlTag)parent;
    return null;
  }

  @Override
  public XmlTagChild getNextSiblingInTag() {
    final PsiElement nextSibling = getNextSibling();
    if (nextSibling instanceof XmlTagChild) return (XmlTagChild)nextSibling;
    return null;
  }

  @Override
  public XmlTagChild getPrevSiblingInTag() {
    PsiElement prevSibling = getPrevSibling();
    return prevSibling instanceof XmlTagChild ? (XmlTagChild)prevSibling : null;
  }

  @Override
  public Icon getElementIcon(int flags) {
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.Tag);
  }

  protected class XmlStubBasedTagDelegate extends XmlTagDelegate {
    public XmlStubBasedTagDelegate() {
      super(XmlStubBasedTagBase.this);
    }

    @Override
    protected void deleteChildInternalSuper(@NotNull ASTNode child) {
      XmlStubBasedTagBase.this.deleteChildInternalSuper(child);
    }

    @Override
    protected TreeElement addInternalSuper(TreeElement first, ASTNode last, @Nullable ASTNode anchor, @Nullable Boolean before) {
      return XmlStubBasedTagBase.this.addInternalSuper(first, last, anchor, before);
    }
  }
}
