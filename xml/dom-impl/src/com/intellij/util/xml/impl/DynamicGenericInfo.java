// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.reference.SoftReference;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Interner;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.reflect.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class DynamicGenericInfo extends DomGenericInfoEx {
  private static final Key<SoftReference<Interner<ChildrenDescriptionsHolder<?>>>> HOLDERS_CACHE = Key.create("DOM_CHILDREN_HOLDERS_CACHE");
  private final StaticGenericInfo myStaticGenericInfo;
  @NotNull private final DomInvocationHandler myInvocationHandler;
  private volatile boolean myInitialized;
  private volatile ChildrenDescriptionsHolder<AttributeChildDescriptionImpl> myAttributes;
  private volatile ChildrenDescriptionsHolder<FixedChildDescriptionImpl> myFixeds;
  private volatile ChildrenDescriptionsHolder<CollectionChildDescriptionImpl> myCollections;
  private volatile List<CustomDomChildrenDescriptionImpl> myCustomChildren;

  public DynamicGenericInfo(@NotNull final DomInvocationHandler handler, final StaticGenericInfo staticGenericInfo) {
    myInvocationHandler = handler;
    myStaticGenericInfo = staticGenericInfo;

    myAttributes = staticGenericInfo.getAttributes();
    myFixeds = staticGenericInfo.getFixed();
    myCollections = staticGenericInfo.getCollections();
  }

  @Override
  public boolean checkInitialized() {
    if (myInitialized) return true;
    myStaticGenericInfo.buildMethodMaps();

    if (!myInvocationHandler.exists()) return true;

    boolean fromIndexing = FileBasedIndex.getInstance().getFileBeingCurrentlyIndexed() != null;
    return RecursionManager.doPreventingRecursion(Pair.create(myInvocationHandler, fromIndexing), false, () -> {
      DomExtensionsRegistrarImpl registrar = runDomExtenders();

      synchronized (myInvocationHandler) {
        if (!myInitialized) {
          if (registrar != null) {
            applyExtensions(registrar);
          }
          myInitialized = true;
        }
      }
      return Boolean.TRUE;
    }) == Boolean.TRUE;
  }

  private void applyExtensions(DomExtensionsRegistrarImpl registrar) {
    XmlFile file = myInvocationHandler.getFile();

    final List<DomExtensionImpl> fixeds = registrar.getFixeds();
    final List<DomExtensionImpl> collections = registrar.getCollections();
    final List<DomExtensionImpl> attributes = registrar.getAttributes();
    if (!attributes.isEmpty()) {
      ChildrenDescriptionsHolder<AttributeChildDescriptionImpl> newAttributes =
        new ChildrenDescriptionsHolder<>(myStaticGenericInfo.getAttributes());
      for (final DomExtensionImpl extension : attributes) {
        newAttributes.addDescription(extension.addAnnotations(new AttributeChildDescriptionImpl(extension.getXmlName(), extension.getType())));
      }
      myAttributes = internChildrenHolder(file, newAttributes);
    }

    if (!fixeds.isEmpty()) {
      ChildrenDescriptionsHolder<FixedChildDescriptionImpl> newFixeds = new ChildrenDescriptionsHolder<>(myStaticGenericInfo.getFixed());
      for (final DomExtensionImpl extension : fixeds) {
        //noinspection unchecked
        newFixeds.addDescription(extension.addAnnotations(new FixedChildDescriptionImpl(extension.getXmlName(), extension.getType(), extension.getCount(),
                                                                                        ArrayUtilRt.EMPTY_COLLECTION_ARRAY)));
      }
      myFixeds = internChildrenHolder(file, newFixeds);
    }
    if (!collections.isEmpty()) {
      ChildrenDescriptionsHolder<CollectionChildDescriptionImpl> newCollections =
        new ChildrenDescriptionsHolder<>(myStaticGenericInfo.getCollections());
      for (final DomExtensionImpl extension : collections) {
        newCollections.addDescription(extension.addAnnotations(new CollectionChildDescriptionImpl(extension.getXmlName(), extension.getType(),
                                                                                                  Collections.emptyList()
        )));
      }
      myCollections = internChildrenHolder(file, newCollections);
    }

    final List<DomExtensionImpl> customs = registrar.getCustoms();
    myCustomChildren = customs.isEmpty() ? null : ContainerUtil.map(customs, CustomDomChildrenDescriptionImpl::new);
  }

  private static <T extends DomChildDescriptionImpl> ChildrenDescriptionsHolder<T> internChildrenHolder(XmlFile file, ChildrenDescriptionsHolder<T> holder) {
    SoftReference<Interner<ChildrenDescriptionsHolder<?>>> ref = file.getUserData(HOLDERS_CACHE);
    Interner<ChildrenDescriptionsHolder<?>> cache = SoftReference.dereference(ref);
    if (cache == null) {
      cache = Interner.createWeakInterner();
      file.putUserData(HOLDERS_CACHE, new SoftReference<>(cache));
    }
    //noinspection unchecked
    return (ChildrenDescriptionsHolder<T>)cache.intern(holder);
  }

  @Nullable
  private DomExtensionsRegistrarImpl runDomExtenders() {
    DomExtensionsRegistrarImpl registrar = null;
    Project project = myInvocationHandler.getManager().getProject();
    for (DomExtenderEP extenderEP : DomExtenderEP.EP_NAME.getExtensionList()) {
      registrar = extenderEP.extend(project, myInvocationHandler, registrar);
    }

    AbstractDomChildDescriptionImpl description = myInvocationHandler.getChildDescription();
    if (description != null) {
      List<DomExtender<?>> extendersFromParent = description.getUserData(DomExtensionImpl.DOM_EXTENDER_KEY);
      if (extendersFromParent != null) {
        if (registrar == null) registrar = new DomExtensionsRegistrarImpl();
        //noinspection rawtypes
        for (DomExtender extender : extendersFromParent) {
          //noinspection unchecked
          extender.registerExtensions(myInvocationHandler.getProxy(), registrar);
        }
      }
    }
    return registrar;
  }

  @Override
  public GenericDomValue<?> getNameDomElement(DomElement element) {
    return myStaticGenericInfo.getNameDomElement(element);
  }

  @Override
  @NotNull
  public List<? extends CustomDomChildrenDescription> getCustomNameChildrenDescription() {
    checkInitialized();
    if (myCustomChildren != null) return myCustomChildren;
    return myStaticGenericInfo.getCustomNameChildrenDescription();
  }

  @Override
  public String getElementName(DomElement element) {
    return myStaticGenericInfo.getElementName(element);
  }

  @Override
  @NotNull
  public List<AbstractDomChildDescriptionImpl> getChildrenDescriptions() {
    checkInitialized();
    final List<AbstractDomChildDescriptionImpl> list = new ArrayList<>();
    myAttributes.dumpDescriptions(list);
    myFixeds.dumpDescriptions(list);
    myCollections.dumpDescriptions(list);
    list.addAll(myStaticGenericInfo.getCustomNameChildrenDescription());
    return list;
  }

  @Override
  @NotNull
  public List<FixedChildDescriptionImpl> getFixedChildrenDescriptions() {
    checkInitialized();
    return myFixeds.getDescriptions();
  }

  @Override
  @NotNull
  public List<CollectionChildDescriptionImpl> getCollectionChildrenDescriptions() {
    checkInitialized();
    return myCollections.getDescriptions();
  }

  @Override
  public FixedChildDescriptionImpl getFixedChildDescription(String tagName) {
    checkInitialized();
    return myFixeds.findDescription(tagName);
  }

  @Override
  public DomFixedChildDescription getFixedChildDescription(@NonNls String tagName, @NonNls String namespace) {
    checkInitialized();
    return myFixeds.getDescription(tagName, namespace);
  }

  @Override
  public CollectionChildDescriptionImpl getCollectionChildDescription(String tagName) {
    checkInitialized();
    return myCollections.findDescription(tagName);
  }

  @Override
  public DomCollectionChildDescription getCollectionChildDescription(@NonNls String tagName, @NonNls String namespace) {
    checkInitialized();
    return myCollections.getDescription(tagName, namespace);
  }

  @Override
  public AttributeChildDescriptionImpl getAttributeChildDescription(String attributeName) {
    checkInitialized();
    return myAttributes.findDescription(attributeName);
  }


  @Override
  public DomAttributeChildDescription<?> getAttributeChildDescription(@NonNls String attributeName, @NonNls String namespace) {
    checkInitialized();
    return myAttributes.getDescription(attributeName, namespace);
  }

  @Override
  public boolean isTagValueElement() {
    return myStaticGenericInfo.isTagValueElement();
  }

  @Override
  @NotNull
  public List<AttributeChildDescriptionImpl> getAttributeChildrenDescriptions() {
    checkInitialized();
    return myAttributes.getDescriptions();
  }

  @Override
  public boolean processAttributeChildrenDescriptions(final Processor<? super AttributeChildDescriptionImpl> processor) {
    final Set<AttributeChildDescriptionImpl> visited = new HashSet<>();
    if (!myStaticGenericInfo.processAttributeChildrenDescriptions(attributeChildDescription -> {
      visited.add(attributeChildDescription);
      return processor.process(attributeChildDescription);
    })) {
      return false;
    }
    for (final AttributeChildDescriptionImpl description : getAttributeChildrenDescriptions()) {
      if (!visited.contains(description) && !processor.process(description)) {
        return false;
      }
    }
    return true;
  }

}
