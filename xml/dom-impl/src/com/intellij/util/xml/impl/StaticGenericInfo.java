// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.util.ConstantFunction;
import com.intellij.util.Function;
import com.intellij.util.NotNullFunction;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.*;

public final class StaticGenericInfo extends DomGenericInfoEx {
  private final Class<? extends DomElement> myClass;

  private final ChildrenDescriptionsHolder<AttributeChildDescriptionImpl> myAttributes = new ChildrenDescriptionsHolder<>();
  private final ChildrenDescriptionsHolder<FixedChildDescriptionImpl> myFixed = new ChildrenDescriptionsHolder<>();
  private final ChildrenDescriptionsHolder<CollectionChildDescriptionImpl> myCollections = new ChildrenDescriptionsHolder<>();

  private Map<JavaMethodSignature, Pair<FixedChildDescriptionImpl, Integer>> myFixedChildrenMethods;
  private Map<JavaMethodSignature, CollectionChildDescriptionImpl> myCollectionChildrenGetterMethods;
  private final Map<JavaMethodSignature, CollectionChildDescriptionImpl> myCollectionChildrenAdditionMethods = new HashMap<>();
  private Map<JavaMethodSignature, AttributeChildDescriptionImpl> myAttributeChildrenMethods;

  private final Map<JavaMethodSignature, Set<CollectionChildDescriptionImpl>> myCompositeChildrenMethods = new HashMap<>();
  private final Map<JavaMethodSignature, Pair<CollectionChildDescriptionImpl, Set<CollectionChildDescriptionImpl>>> myCompositeCollectionAdditionMethods =
    new HashMap<>();

  @Nullable private JavaMethod myNameValueGetter;
  private boolean myValueElement;
  private boolean myInitialized;
  private CustomDomChildrenDescriptionImpl myCustomDescription;

  StaticGenericInfo(Class<? extends DomElement> clazz) {
    myClass = clazz;
  }

  synchronized boolean buildMethodMaps() {
    if (!myInitialized) {
      final StaticGenericInfoBuilder builder = new StaticGenericInfoBuilder(myClass);
      final JavaMethod customChildrenGetter = builder.getCustomChildrenGetter();
      if (customChildrenGetter != null) {
        myCustomDescription = new CustomDomChildrenDescriptionImpl(customChildrenGetter);
      }

      myAttributeChildrenMethods = builder.getAttributes();
      myAttributes.addDescriptions(myAttributeChildrenMethods.values());

      myFixedChildrenMethods = builder.getFixedGetters();
      for (final Pair<FixedChildDescriptionImpl, Integer> pair : myFixedChildrenMethods.values()) {
        myFixed.addDescription(pair.first);
      }

      myCollectionChildrenGetterMethods = builder.getCollectionGetters();
      myCollections.addDescriptions(myCollectionChildrenGetterMethods.values());


      for (final CollectionChildDescriptionImpl description : myCollectionChildrenGetterMethods.values()) {
        final XmlName name = description.getXmlName();
        addAdders(description, builder.collectionAdders.get(name));
        addAdders(description, builder.collectionIndexAdders.get(name));
        addAdders(description, builder.collectionIndexClassAdders.get(name));
        addAdders(description, builder.collectionClassIndexAdders.get(name));
        addAdders(description, builder.collectionClassAdders.get(name));
      }

      final NotNullFunction<String, CollectionChildDescriptionImpl> mapper =
        xmlName -> Objects.requireNonNull(myCollections.findDescription(xmlName));
      final Map<JavaMethodSignature, String[]> getters = builder.getCompositeCollectionGetters();
      for (final JavaMethodSignature signature : getters.keySet()) {
        myCompositeChildrenMethods.put(signature, ContainerUtil.map2Set(getters.get(signature), mapper));
      }
      final Map<JavaMethodSignature, Pair<String, String[]>> adders = builder.getCompositeCollectionAdders();
      for (final JavaMethodSignature signature : adders.keySet()) {
        final Pair<String, String[]> pair = adders.get(signature);
        myCompositeCollectionAdditionMethods.put(signature, Pair.create(myCollections.findDescription(pair.first), ContainerUtil.map2Set(pair.second, mapper)));
      }
      myNameValueGetter = builder.getNameValueGetter();
      myValueElement = builder.isValueElement();
      myInitialized = true;
    }
    return true;
  }

  private void addAdders(final CollectionChildDescriptionImpl description, final Collection<JavaMethod> methods) {
    if (methods != null) {
      for (final JavaMethod method : methods) {
        myCollectionChildrenAdditionMethods.put(method.getSignature(), description);
      }
    }
  }

  @Override
  public boolean checkInitialized() {
    return buildMethodMaps();
  }

  Invocation createInvocation(JavaMethod method) {
    buildMethodMaps();

    final JavaMethodSignature signature = method.getSignature();
    final PropertyAccessor accessor = method.getAnnotation(PropertyAccessor.class);
    if (accessor != null) {
      return new PropertyAccessorInvocation(DomReflectionUtil.getGetterMethods(accessor.value(), myClass));
    }

    if (myAttributeChildrenMethods.containsKey(signature)) {
      return new GetAttributeChildInvocation(myAttributeChildrenMethods.get(signature));
    }

    if (myFixedChildrenMethods.containsKey(signature)) {
      return new GetFixedChildInvocation(myFixedChildrenMethods.get(signature));
    }

    final Set<CollectionChildDescriptionImpl> qnames = myCompositeChildrenMethods.get(signature);
    if (qnames != null) {
      return new GetCompositeCollectionInvocation(qnames);
    }

    if (myCustomDescription != null && method.equals(myCustomDescription.getGetterMethod())) {
      return (handler, args) -> myCustomDescription.getValues(handler);
    }

    final Pair<CollectionChildDescriptionImpl, Set<CollectionChildDescriptionImpl>> pair = myCompositeCollectionAdditionMethods.get(signature);
    if (pair != null) {
      return new AddToCompositeCollectionInvocation(pair.first, pair.second, method.getGenericReturnType());
    }

    CollectionChildDescriptionImpl description = myCollectionChildrenGetterMethods.get(signature);
    if (description != null) {
      return new GetCollectionChildInvocation(description);
    }

    description = myCollectionChildrenAdditionMethods.get(signature);
    if (description != null) {
      return new AddChildInvocation(getTypeGetter(method), getIndexGetter(method), description, description.getType());
    }

    return null;
  }

  private static Function<Object[], Type> getTypeGetter(final JavaMethod method) {
    if (method.getParameterCount() >= 1 && method.getParameterTypes()[0].equals(Class.class)) {
      return s -> (Type)s[0];
    }

    if (method.getParameterCount() == 2 && method.getParameterTypes()[1].equals(Class.class)) {
      return s -> (Type)s[1];
    }

    return s -> method.getGenericReturnType();
  }


  private static Function<Object[], Integer> getIndexGetter(final JavaMethod method) {
    if (method.getParameterCount() >= 1 && method.getParameterTypes()[0].equals(int.class)) {
      return s -> (Integer)s[0];
    }

    if (method.getParameterCount() == 2 && method.getParameterTypes()[1].equals(int.class)) {
      return s -> (Integer)s[1];
    }

    return new ConstantFunction<>(Integer.MAX_VALUE);
  }

  @Override
  @Nullable
  public GenericDomValue getNameDomElement(DomElement element) {
    buildMethodMaps();

    Object o = getNameObject(element);
    return o instanceof GenericDomValue ? (GenericDomValue)o : null;
  }

  @Override
  @NotNull
  public List<? extends CustomDomChildrenDescriptionImpl> getCustomNameChildrenDescription() {
    return myCustomDescription == null ? Collections.emptyList() : Collections.singletonList(myCustomDescription);
  }

  @Nullable
  private Object getNameObject(DomElement element) {
    return myNameValueGetter == null ? null : myNameValueGetter.invoke(element);
  }

  @Override
  @Nullable
  public String getElementName(DomElement element) {
    buildMethodMaps();
    Object o = getNameObject(element);
    return o == null || o instanceof String ? (String)o : ((GenericValue<?>)o).getStringValue();
  }

  @Override
  @NotNull
  public List<AbstractDomChildDescriptionImpl> getChildrenDescriptions() {
    buildMethodMaps();
    List<AbstractDomChildDescriptionImpl> list = new ArrayList<>();
    myAttributes.dumpDescriptions(list);
    myFixed.dumpDescriptions(list);
    myCollections.dumpDescriptions(list);
    list.addAll(getCustomNameChildrenDescription());
    return list;
  }

  @Override
  @NotNull
  public List<? extends DomFixedChildDescription> getFixedChildrenDescriptions() {
    buildMethodMaps();
    return myFixed.getDescriptions();
  }

  @Override
  @NotNull
  public List<? extends DomCollectionChildDescription> getCollectionChildrenDescriptions() {
    buildMethodMaps();
    return myCollections.getDescriptions();
  }

  @Override
  public boolean isTagValueElement() {
    buildMethodMaps();
    return myValueElement;
  }

  @Override
  @NotNull
  public List<AttributeChildDescriptionImpl> getAttributeChildrenDescriptions() {
    buildMethodMaps();
    return new ArrayList<>(myAttributeChildrenMethods.values());
  }

  @Override
  public boolean processAttributeChildrenDescriptions(Processor<? super AttributeChildDescriptionImpl> processor) {
    List<AttributeChildDescriptionImpl> descriptions = getAttributeChildrenDescriptions();
    return ContainerUtil.process(descriptions, processor);
  }

  @Override
  @Nullable
  public DomFixedChildDescription getFixedChildDescription(@NonNls final String tagName) {
    buildMethodMaps();
    return myFixed.findDescription(tagName);
  }

  @Override
  @Nullable
  public DomFixedChildDescription getFixedChildDescription(@NonNls final String tagName, @NonNls final String namespaceKey) {
    buildMethodMaps();
    return myFixed.getDescription(tagName, namespaceKey);
  }

  @Override
  @Nullable
  public DomCollectionChildDescription getCollectionChildDescription(@NonNls final String tagName) {
    buildMethodMaps();
    return myCollections.findDescription(tagName);
  }

  @Override
  @Nullable
  public DomCollectionChildDescription getCollectionChildDescription(@NonNls final String tagName, @NonNls final String namespaceKey) {
    buildMethodMaps();
    return myCollections.getDescription(tagName, namespaceKey);
  }

  @Override
  @Nullable
  public DomAttributeChildDescription getAttributeChildDescription(@NonNls final String attributeName) {
    buildMethodMaps();
    return myAttributes.findDescription(attributeName);
  }

  @Override
  @Nullable
  public DomAttributeChildDescription getAttributeChildDescription(@NonNls final String attributeName, @NonNls final String namespaceKey) {
    buildMethodMaps();
    return myAttributes.getDescription(attributeName, namespaceKey);
  }

  public ChildrenDescriptionsHolder<AttributeChildDescriptionImpl> getAttributes() {
    return myAttributes;
  }

  public ChildrenDescriptionsHolder<CollectionChildDescriptionImpl> getCollections() {
    return myCollections;
  }

  public ChildrenDescriptionsHolder<FixedChildDescriptionImpl> getFixed() {
    return myFixed;
  }
}
