// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.impl.PsiCachedValueImpl;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.*;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.XmlNsDescriptorUtil;
import com.intellij.xml.util.XmlPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.xml.impl.XmlNsDescriptorUtil.isGeneratedFromDtd;

public class XmlDocumentImpl extends XmlElementImpl implements XmlDocument {
  private static final AtomicFieldUpdater<XmlDocumentImpl, XmlProlog>
    MY_PROLOG_UPDATER = AtomicFieldUpdater.forFieldOfType(XmlDocumentImpl.class, XmlProlog.class);
  private static final AtomicFieldUpdater<XmlDocumentImpl, XmlTag>
    MY_ROOT_TAG_UPDATER = AtomicFieldUpdater.forFieldOfType(XmlDocumentImpl.class, XmlTag.class);

  private volatile XmlProlog myProlog;
  private volatile XmlTag myRootTag;
  private volatile long myExtResourcesModCount = -1;

  public XmlDocumentImpl() {
    this(XmlElementType.XML_DOCUMENT);
  }

  protected XmlDocumentImpl(IElementType type) {
    super(type);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlDocument(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public XmlProlog getProlog() {
    XmlProlog prolog = myProlog;

    if (prolog == null) {
      prolog = (XmlProlog)findElementByTokenType(XmlElementType.XML_PROLOG);

      if(!MY_PROLOG_UPDATER.compareAndSet(this, null, prolog)) {
        prolog = MY_PROLOG_UPDATER.getVolatile(this);
      }
    }

    return prolog;
  }

  @Override
  public XmlTag getRootTag() {
    XmlTag rootTag = myRootTag;

    if (rootTag == null) {
      rootTag = (XmlTag)XmlPsiUtil.findElement(this, IXmlTagElementType.class::isInstance);

      if (!MY_ROOT_TAG_UPDATER.compareAndSet(this, null, rootTag)) {
        rootTag = MY_ROOT_TAG_UPDATER.getVolatile(this);
      }
    }

    return rootTag;
  }

  @Override
  public XmlNSDescriptor getRootTagNSDescriptor() {
    XmlTag rootTag = getRootTag();
    return rootTag != null ? rootTag.getNSDescriptor(rootTag.getNamespace(), false) : null;
  }

  private ConcurrentMap<String, CachedValue<XmlNSDescriptor>> myDefaultDescriptorsCacheStrict =
    new ConcurrentHashMap<>();
  private ConcurrentMap<String, CachedValue<XmlNSDescriptor>> myDefaultDescriptorsCacheNotStrict =
    new ConcurrentHashMap<>();

  @Override
  public void clearCaches() {
    myDefaultDescriptorsCacheStrict.clear();
    myDefaultDescriptorsCacheNotStrict.clear();
    myRootTag = null;
    myProlog = null;
    super.clearCaches();
  }

  @Override
  public @Nullable XmlNSDescriptor getDefaultNSDescriptor(final String namespace, final boolean strict) {
    long curExtResourcesModCount = ExternalResourceManagerEx.getInstanceEx().getModificationCount(getProject());
    if (myExtResourcesModCount != curExtResourcesModCount) {
      myDefaultDescriptorsCacheNotStrict.clear();
      myDefaultDescriptorsCacheStrict.clear();
      myExtResourcesModCount = curExtResourcesModCount;
    }

    final ConcurrentMap<String, CachedValue<XmlNSDescriptor>> defaultDescriptorsCache;
    if (strict) {
      defaultDescriptorsCache = myDefaultDescriptorsCacheStrict;
    }
    else {
      defaultDescriptorsCache = myDefaultDescriptorsCacheNotStrict;
    }

    CachedValue<XmlNSDescriptor> cachedValue = defaultDescriptorsCache.get(namespace);
    if (cachedValue == null) {
      defaultDescriptorsCache.put(namespace, cachedValue = new PsiCachedValueImpl.Soft<>(getManager(), () -> {
        final XmlNSDescriptor defaultNSDescriptorInner = XmlNsDescriptorUtil.getDefaultNSDescriptor(this, namespace, strict);

        if (isGeneratedFromDtd(this, defaultNSDescriptorInner)) {
          return new CachedValueProvider.Result<>(defaultNSDescriptorInner, this, ExternalResourceManager.getInstance());
        }

        return new CachedValueProvider.Result<>(defaultNSDescriptorInner, defaultNSDescriptorInner != null
                                                                          ? defaultNSDescriptorInner.getDependencies()
                                                                          : ExternalResourceManager.getInstance());
      }));
    }
    return cachedValue.getValue();
  }

  public static @Nullable XmlNSDescriptor getCachedHtmlNsDescriptor(final @NotNull XmlFile descriptorFile) {
    return XmlNsDescriptorUtil.getCachedHtmlNsDescriptor(descriptorFile, "");
  }

  @Override
  public @NotNull CompositePsiElement clone() {
    HashMap<String, CachedValue<XmlNSDescriptor>> cacheStrict = new HashMap<>(
      myDefaultDescriptorsCacheStrict
    );
    HashMap<String, CachedValue<XmlNSDescriptor>> cacheNotStrict = new HashMap<>(
      myDefaultDescriptorsCacheNotStrict
    );
    final XmlDocumentImpl copy = (XmlDocumentImpl) super.clone();
    updateSelfDependentDtdDescriptors(copy, cacheStrict, cacheNotStrict);
    return copy;
  }

  @Override
  public PsiElement copy() {
    HashMap<String, CachedValue<XmlNSDescriptor>> cacheStrict = new HashMap<>(
      myDefaultDescriptorsCacheStrict
    );
    HashMap<String, CachedValue<XmlNSDescriptor>> cacheNotStrict = new HashMap<>(
      myDefaultDescriptorsCacheNotStrict
    );
    final XmlDocumentImpl copy = (XmlDocumentImpl)super.copy();
    updateSelfDependentDtdDescriptors(copy, cacheStrict, cacheNotStrict);
    return copy;
  }

  private void updateSelfDependentDtdDescriptors(XmlDocumentImpl copy, HashMap<String,
    CachedValue<XmlNSDescriptor>> cacheStrict, HashMap<String, CachedValue<XmlNSDescriptor>> cacheNotStrict) {
    copy.myDefaultDescriptorsCacheNotStrict = new ConcurrentHashMap<>();
    copy.myDefaultDescriptorsCacheStrict = new ConcurrentHashMap<>();

    for(Map.Entry<String, CachedValue<XmlNSDescriptor>> e:cacheStrict.entrySet()) {
      if (e.getValue().hasUpToDateValue()) {
        final XmlNSDescriptor nsDescriptor = e.getValue().getValue();
        if (!isGeneratedFromDtd(this, nsDescriptor)) copy.myDefaultDescriptorsCacheStrict.put(e.getKey(), e.getValue());
      }
    }

    for(Map.Entry<String, CachedValue<XmlNSDescriptor>> e:cacheNotStrict.entrySet()) {
      if (e.getValue().hasUpToDateValue()) {
        final XmlNSDescriptor nsDescriptor = e.getValue().getValue();
        if (!isGeneratedFromDtd(this, nsDescriptor)) copy.myDefaultDescriptorsCacheNotStrict.put(e.getKey(), e.getValue());
      }
    }
  }

  @Override
  public PsiMetaData getMetaData() {
    return MetaRegistry.getMeta(this);
  }

}
