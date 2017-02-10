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
package com.intellij.util.xml.impl;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.reference.SoftReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.WeakInterner;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.JavaMethod;
import com.intellij.util.xml.reflect.*;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public class DynamicGenericInfo extends DomGenericInfoEx {
  private static final Key<SoftReference<WeakInterner<ChildrenDescriptionsHolder>>> HOLDERS_CACHE = Key.create("DOM_CHILDREN_HOLDERS_CACHE");
  private static final RecursionGuard ourGuard = RecursionManager.createGuard("dynamicGenericInfo");
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
  public Invocation createInvocation(final JavaMethod method) {
    return myStaticGenericInfo.createInvocation(method);
  }

  @Override
  public final boolean checkInitialized() {
    if (myInitialized) return true;
    myStaticGenericInfo.buildMethodMaps();

    if (!myInvocationHandler.exists()) return true;

    return ourGuard.doPreventingRecursion(myInvocationHandler, false, () -> {
      DomExtensionsRegistrarImpl registrar = runDomExtenders();

      //noinspection SynchronizationOnLocalVariableOrMethodParameter
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
        newFixeds.addDescription(extension.addAnnotations(new FixedChildDescriptionImpl(extension.getXmlName(), extension.getType(), extension.getCount(), ArrayUtil.EMPTY_COLLECTION_ARRAY)));
      }
      myFixeds = internChildrenHolder(file, newFixeds);
    }
    if (!collections.isEmpty()) {
      ChildrenDescriptionsHolder<CollectionChildDescriptionImpl> newCollections =
        new ChildrenDescriptionsHolder<>(myStaticGenericInfo.getCollections());
      for (final DomExtensionImpl extension : collections) {
        newCollections.addDescription(extension.addAnnotations(new CollectionChildDescriptionImpl(extension.getXmlName(), extension.getType(),
                                                                                                  Collections.<JavaMethod>emptyList()
        )));
      }
      myCollections = internChildrenHolder(file, newCollections);
    }

    final List<DomExtensionImpl> customs = registrar.getCustoms();
    myCustomChildren = customs.isEmpty() ? null : ContainerUtil.map(customs, extension -> new CustomDomChildrenDescriptionImpl(extension));
  }

  private static <T extends DomChildDescriptionImpl> ChildrenDescriptionsHolder<T> internChildrenHolder(XmlFile file, ChildrenDescriptionsHolder<T> holder) {
    SoftReference<WeakInterner<ChildrenDescriptionsHolder>> ref = file.getUserData(HOLDERS_CACHE);
    WeakInterner<ChildrenDescriptionsHolder> cache = SoftReference.dereference(ref);
    if (cache == null) {
      cache = new WeakInterner<>();
      file.putUserData(HOLDERS_CACHE, new SoftReference<>(cache));
    }
    //noinspection unchecked
    return cache.intern(holder);
  }

  @Nullable
  private DomExtensionsRegistrarImpl runDomExtenders() {
    DomExtensionsRegistrarImpl registrar = null;
    final Project project = myInvocationHandler.getManager().getProject();
    DomExtenderEP[] extenders = Extensions.getExtensions(DomExtenderEP.EP_NAME);
    if (extenders.length > 0) {
      for (final DomExtenderEP extenderEP : extenders) {
        registrar = extenderEP.extend(project, myInvocationHandler, registrar);
      }
    }

    final AbstractDomChildDescriptionImpl description = myInvocationHandler.getChildDescription();
    if (description != null) {
      final List<DomExtender> extendersFromParent = description.getUserData(DomExtensionImpl.DOM_EXTENDER_KEY);
      if (extendersFromParent != null) {
        if (registrar == null) registrar = new DomExtensionsRegistrarImpl();
        for (final DomExtender extender : extendersFromParent) {
          //noinspection unchecked
          extender.registerExtensions(myInvocationHandler.getProxy(), registrar);
        }
      }
    }
    return registrar;
  }

  @Override
  public XmlElement getNameElement(DomElement element) {
    return myStaticGenericInfo.getNameElement(element);
  }

  @Override
  public GenericDomValue getNameDomElement(DomElement element) {
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
    final ArrayList<AbstractDomChildDescriptionImpl> list = new ArrayList<>();
    myAttributes.dumpDescriptions(list);
    myFixeds.dumpDescriptions(list);
    myCollections.dumpDescriptions(list);
    list.addAll(myStaticGenericInfo.getCustomNameChildrenDescription());
    return list;
  }

  @Override
  @NotNull
  public final List<FixedChildDescriptionImpl> getFixedChildrenDescriptions() {
    checkInitialized();
    return myFixeds.getDescriptions();
  }

  @Override
  @NotNull
  public final List<CollectionChildDescriptionImpl> getCollectionChildrenDescriptions() {
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
  public DomAttributeChildDescription getAttributeChildDescription(@NonNls String attributeName, @NonNls String namespace) {
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
  public boolean processAttributeChildrenDescriptions(final Processor<AttributeChildDescriptionImpl> processor) {
    final Set<AttributeChildDescriptionImpl> visited = new THashSet<>();
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
