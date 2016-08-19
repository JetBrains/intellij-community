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

import com.intellij.openapi.util.Pair;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.*;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.Type;
import java.util.*;

/**
 * @author peter
 */
public class StaticGenericInfo extends DomGenericInfoEx {
  private final Class myClass;

  private final ChildrenDescriptionsHolder<AttributeChildDescriptionImpl> myAttributes = new ChildrenDescriptionsHolder<>();
  private final ChildrenDescriptionsHolder<FixedChildDescriptionImpl> myFixed = new ChildrenDescriptionsHolder<>();
  private final ChildrenDescriptionsHolder<CollectionChildDescriptionImpl> myCollections = new ChildrenDescriptionsHolder<>();

  private Map<JavaMethodSignature, Pair<FixedChildDescriptionImpl, Integer>> myFixedChildrenMethods;
  private Map<JavaMethodSignature, CollectionChildDescriptionImpl> myCollectionChildrenGetterMethods;
  private final Map<JavaMethodSignature, CollectionChildDescriptionImpl> myCollectionChildrenAdditionMethods = new THashMap<>();
  private Map<JavaMethodSignature, AttributeChildDescriptionImpl> myAttributeChildrenMethods;

  private final Map<JavaMethodSignature, Set<CollectionChildDescriptionImpl>> myCompositeChildrenMethods = new THashMap<>();
  private final Map<JavaMethodSignature, Pair<CollectionChildDescriptionImpl, Set<CollectionChildDescriptionImpl>>> myCompositeCollectionAdditionMethods =
    new THashMap<>();

  @Nullable private JavaMethod myNameValueGetter;
  private boolean myValueElement;
  private boolean myInitialized;
  private CustomDomChildrenDescriptionImpl myCustomDescription;

  public StaticGenericInfo(Class clazz) {
    myClass = clazz;
  }

  public final synchronized boolean buildMethodMaps() {
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
        xmlName -> ObjectUtils.assertNotNull(myCollections.findDescription(xmlName));
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

  @Override
  public final Invocation createInvocation(final JavaMethod method) {
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
      return new Invocation() {
        @Override
        @Nullable
        public Object invoke(final DomInvocationHandler<?, ?> handler, final Object[] args) throws Throwable {
          return myCustomDescription.getValues(handler);
        }
      };
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
    final Class<?>[] parameterTypes = method.getParameterTypes();
    if (parameterTypes.length >= 1 && parameterTypes[0].equals(Class.class)) {
      return new Function.First<>();
    }

    if (parameterTypes.length == 2 && parameterTypes[1].equals(Class.class)) {
      return s -> (Type)s[1];
    }

    return s -> method.getGenericReturnType();
  }


  private static Function<Object[], Integer> getIndexGetter(final JavaMethod method) {
    final Class<?>[] parameterTypes = method.getParameterTypes();
    if (parameterTypes.length >= 1 && parameterTypes[0].equals(int.class)) {
      return new Function.First<>();
    }

    if (parameterTypes.length == 2 && parameterTypes[1].equals(int.class)) {
      return s -> (Integer)s[1];
    }

    return new ConstantFunction<>(Integer.MAX_VALUE);
  }

  @Override
  @Nullable
  public XmlElement getNameElement(DomElement element) {
    buildMethodMaps();

    Object o = getNameObject(element);
    if (o instanceof GenericAttributeValue) {
      return ((GenericAttributeValue)o).getXmlAttributeValue();
    } else if (o instanceof DomElement) {
      return ((DomElement)o).getXmlTag();
    }
    else {
      return null;
    }
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
    return myCustomDescription == null ? Collections.<CustomDomChildrenDescriptionImpl>emptyList() : Collections.singletonList(myCustomDescription);
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
    return o == null || o instanceof String ? (String)o : ((GenericValue)o).getStringValue();
  }

  @Override
  @NotNull
  public List<AbstractDomChildDescriptionImpl> getChildrenDescriptions() {
    buildMethodMaps();
    final ArrayList<AbstractDomChildDescriptionImpl> list = new ArrayList<>();
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
  public boolean processAttributeChildrenDescriptions(Processor<AttributeChildDescriptionImpl> processor) {
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
