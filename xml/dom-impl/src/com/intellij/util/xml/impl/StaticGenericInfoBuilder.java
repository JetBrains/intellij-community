// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.serialization.ClassUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xml.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public final class StaticGenericInfoBuilder {
  private static final Set<Class<?>> ADDER_PARAMETER_TYPES = Set.of(Class.class, int.class);
  private static final Logger LOG = Logger.getInstance(StaticGenericInfoBuilder.class);
  private final Class myClass;
  private final MultiValuesMap<XmlName, JavaMethod> myCollectionGetters = new MultiValuesMap<>();
  final MultiValuesMap<XmlName, JavaMethod> collectionAdders = new MultiValuesMap<>();
  final MultiValuesMap<XmlName, JavaMethod> collectionClassAdders = new MultiValuesMap<>();
  final MultiValuesMap<XmlName, JavaMethod> collectionIndexAdders = new MultiValuesMap<>();
  final MultiValuesMap<XmlName, JavaMethod> collectionIndexClassAdders = new MultiValuesMap<>();
  final MultiValuesMap<XmlName, JavaMethod> collectionClassIndexAdders = new MultiValuesMap<>();
  private final Map<XmlName, Type> myCollectionChildrenTypes = new HashMap<>();
  private final Map<JavaMethodSignature, String[]> myCompositeCollectionGetters = new HashMap<>();
  private final Map<JavaMethodSignature, Pair<String,String[]>> myCompositeCollectionAdders = new HashMap<>();
  private final Map<XmlName, Int2ObjectMap<Collection<JavaMethod>>> myFixedChildrenGetters = FactoryMap.create(key -> new Int2ObjectOpenHashMap<>());
  private final Map<JavaMethodSignature, AttributeChildDescriptionImpl> myAttributes = new HashMap<>();

  private boolean myValueElement;
  private JavaMethod myNameValueGetter;
  private JavaMethod myCustomChildrenGetter;

  public StaticGenericInfoBuilder(final Class aClass) {
    myClass = aClass;

    final Set<JavaMethod> methods = new LinkedHashSet<>();
    InvocationCache invocationCache = DomApplicationComponent.getInstance().getInvocationCache(myClass);
    for (final Method method : ReflectionUtil.getClassPublicMethods(myClass)) {
      methods.add(invocationCache.getInternedMethod(method));
    }
    for (final JavaMethod method : methods) {
      if (DomImplUtil.isGetter(method) && method.getAnnotation(NameValue.class) != null) {
        myNameValueGetter = method;
        break;
      }
    }

    {
      final Class<?> implClass = DomApplicationComponent.getInstance().getImplementation(myClass);
      if (implClass != null) {
        for (Method method : ReflectionUtil.getClassPublicMethods(implClass)) {
          final int modifiers = method.getModifiers();
          if (!Modifier.isAbstract(modifiers) &&
              !Modifier.isVolatile(modifiers) &&
              new JavaMethodSignature(method).findMethod(myClass) != null) {
            methods.remove(invocationCache.getInternedMethod(method));
          }
        }
      }
    }

    for (Iterator<JavaMethod> iterator = methods.iterator(); iterator.hasNext();) {
      final JavaMethod method = iterator.next();
      if (isCoreMethod(method) || DomImplUtil.isTagValueSetter(method) || method.getAnnotation(PropertyAccessor.class) != null) {
        iterator.remove();
      }
    }

    for (Iterator<JavaMethod> iterator = methods.iterator(); iterator.hasNext();) {
      final JavaMethod method = iterator.next();
      if (DomImplUtil.isGetter(method) && processGetterMethod(method)) {
        iterator.remove();
      }
    }

    for (Iterator<JavaMethod> iterator = methods.iterator(); iterator.hasNext();) {
      final JavaMethod method = iterator.next();
      final SubTagsList subTagsList = method.getAnnotation(SubTagsList.class);
      if (subTagsList != null && method.getName().startsWith("add")) {
        final String localName = subTagsList.tagName();
        assert StringUtil.isNotEmpty(localName);
        final String[] set = subTagsList.value();
        assert Arrays.asList(set).contains(localName);
        myCompositeCollectionAdders.put(method.getSignature(), Pair.create(localName, set));
        iterator.remove();
      }
      else if (isAddMethod(method)) {
        final XmlName xmlName = extractTagName(method, "add");
        if (myCollectionGetters.containsKey(xmlName)) {
          MultiValuesMap<XmlName, JavaMethod> adders = getAddersMap(method);
          if (adders != null) {
            adders.put(xmlName, method);
            iterator.remove();
          }
        }
      }
    }

    // noinspection ConstantConditions
    if (false) {
      if (!methods.isEmpty()) {
        assert false : methods.stream().map(method -> "\n  " + method)
          .collect(Collectors.joining("", myClass + " should provide the following implementations:", ""));
      }
    }
  }

  @Nullable
  private MultiValuesMap<XmlName, JavaMethod> getAddersMap(final JavaMethod method) {
    final Class<?>[] parameterTypes = method.getParameterTypes();
    return switch (parameterTypes.length) {
      case 0 -> collectionAdders;
      case 1 -> {
        if (Class.class.equals(parameterTypes[0])) yield collectionClassAdders;
        if (isInt(parameterTypes[0])) yield collectionIndexAdders;
        yield null;
      }
      case 2 -> {
        if (isIndexClassAdder(parameterTypes[0], parameterTypes[1])) yield collectionIndexClassAdders;
        if (isIndexClassAdder(parameterTypes[1], parameterTypes[0])) yield collectionClassIndexAdders;
        yield null;
      }
      default -> null;
    };
  }

  private static boolean isIndexClassAdder(final Class<?> first, final Class<?> second) {
    return isInt(first) && second.equals(Class.class);
  }

  private static boolean isInt(final Class<?> aClass) {
    return aClass.equals(int.class) || aClass.equals(Integer.class);
  }

  private boolean isAddMethod(JavaMethod method) {
    final XmlName tagName = extractTagName(method, "add");
    if (tagName == null) return false;

    final Type type = myCollectionChildrenTypes.get(tagName);
    if (type == null || !ClassUtil.getRawType(type).isAssignableFrom(method.getReturnType())) return false;

    if (method.getParameterCount() == 0) return true;

    return ADDER_PARAMETER_TYPES.containsAll(Arrays.asList(method.getParameterTypes()));
  }

  @Nullable
  private XmlName extractTagName(JavaMethod method, @NonNls String prefix) {
    final String name = method.getName();
    if (!name.startsWith(prefix)) return null;

    final SubTagList subTagAnnotation = method.getAnnotation(SubTagList.class);
    if (subTagAnnotation != null && !StringUtil.isEmpty(subTagAnnotation.value())) {
      return DomImplUtil.createXmlName(subTagAnnotation.value(), method);
    }

    final String tagName = getNameStrategy(false).convertName(name.substring(prefix.length()));
    return StringUtil.isEmpty(tagName) ? null : DomImplUtil.createXmlName(tagName, method);
  }

  private static boolean isDomElement(final Type type) {
    return type != null && DomElement.class.isAssignableFrom(ClassUtil.getRawType(type));
  }

  private boolean processGetterMethod(final JavaMethod method) {
    if (DomImplUtil.isTagValueGetter(method)) {
      myValueElement = true;
      return true;
    }

    final Class returnType = method.getReturnType();
    final boolean isAttributeValueMethod = GenericAttributeValue.class.isAssignableFrom(returnType);
    final JavaMethodSignature signature = method.getSignature();
    final Attribute annotation = method.getAnnotation(Attribute.class);
    final boolean isAttributeMethod = annotation != null || isAttributeValueMethod;
    if (annotation != null) {
      assert
        isAttributeValueMethod || GenericAttributeValue.class.isAssignableFrom(returnType) :
        method + " should return GenericAttributeValue";
    }
    if (isAttributeMethod) {
      final String s = annotation == null ? null : annotation.value();
      String attributeName = StringUtil.isEmpty(s) ? getNameFromMethod(method, true) : s;
      assert attributeName != null && StringUtil.isNotEmpty(attributeName) : "Can't guess attribute name from method name: " + method.getName();
      final XmlName attrName = DomImplUtil.createXmlName(attributeName, method);
      myAttributes.put(signature, new AttributeChildDescriptionImpl(attrName, method));
      return true;
    }

    if (isDomElement(returnType)) {
      final String qname = getSubTagName(method);
      if (qname != null) {
        final XmlName xmlName = DomImplUtil.createXmlName(qname, method);
        Type collectionType = myCollectionChildrenTypes.get(xmlName);
        if (collectionType != null) {
          LOG.error("Collection (" + collectionType + ") and fixed children cannot intersect: " + qname + " for " + myClass);
        }
        int index = 0;
        final SubTag subTagAnnotation = method.getAnnotation(SubTag.class);
        if (subTagAnnotation != null && subTagAnnotation.index() != 0) {
          index = subTagAnnotation.index();
        }
        Int2ObjectMap<Collection<JavaMethod>> map = myFixedChildrenGetters.get(xmlName);
        Collection<JavaMethod> methods = map.get(index);
        if (methods == null) {
          map.put(index, methods = new SmartList<>());
        }
        methods.add(method);
        return true;
      }
    }

    final Type type = DomReflectionUtil.extractCollectionElementType(method.getGenericReturnType());
    if (isDomElement(type)) {
      final CustomChildren customChildren = method.getAnnotation(CustomChildren.class);
      if (customChildren != null) {
        myCustomChildrenGetter = method;
        return true;
      }

      final SubTagsList subTagsList = method.getAnnotation(SubTagsList.class);
      if (subTagsList != null) {
        myCompositeCollectionGetters.put(signature, subTagsList.value());
        return true;
      }

      final String qname = getSubTagNameForCollection(method);
      if (qname != null) {
        XmlName xmlName = DomImplUtil.createXmlName(qname, type, method);
        assert !myFixedChildrenGetters.containsKey(xmlName) : "Collection and fixed children cannot intersect: " + qname;
        myCollectionChildrenTypes.put(xmlName, type);
        myCollectionGetters.put(xmlName, method);
        return true;
      }
    }

    return false;
  }


  private static final Set<JavaMethodSignature> ourDomElementMethods =
    ContainerUtil.map2Set(DomElement.class.getMethods(), method -> new JavaMethodSignature(method));

  private static boolean isCoreMethod(final JavaMethod method) {
    if (ourDomElementMethods.contains(method.getSignature())) return true;

    final Class<?> aClass = method.getDeclaringClass();
    return aClass.equals(GenericAttributeValue.class) || aClass.equals(GenericDomValue.class) && "getConverter".equals(method.getName());
  }

  @Nullable
  private String getSubTagName(final JavaMethod method) {
    final SubTag subTagAnnotation = method.getAnnotation(SubTag.class);
    if (subTagAnnotation == null || StringUtil.isEmpty(subTagAnnotation.value())) {
      return getNameFromMethod(method, false);
    }
    return subTagAnnotation.value();
  }

  @Nullable
  private String getSubTagNameForCollection(final JavaMethod method) {
    final SubTagList subTagList = method.getAnnotation(SubTagList.class);
    if (subTagList == null || StringUtil.isEmpty(subTagList.value())) {
      final String propertyName = getPropertyName(method);
      if (propertyName != null) {
        final String singular = StringUtil.unpluralize(propertyName);
        assert singular != null : "Can't unpluralize: " + propertyName;
        return getNameStrategy(false).convertName(singular);
      }
      else {
        return null;
      }
    }
    return subTagList.value();
  }

  @Nullable
  private String getNameFromMethod(final JavaMethod method, boolean isAttribute) {
    final String propertyName = getPropertyName(method);
    return propertyName == null ? null : getNameStrategy(isAttribute).convertName(propertyName);
  }

  @Nullable
  private static String getPropertyName(JavaMethod method) {
    return StringUtil.getPropertyName(method.getMethodName());
  }

  @NotNull
  private DomNameStrategy getNameStrategy(boolean isAttribute) {
    final DomNameStrategy strategy = DomImplUtil.getDomNameStrategy(ClassUtil.getRawType(myClass), isAttribute);
    return strategy != null ? strategy : DomNameStrategy.HYPHEN_STRATEGY;
  }

  JavaMethod getCustomChildrenGetter() {
    return myCustomChildrenGetter;
  }

  Map<JavaMethodSignature, AttributeChildDescriptionImpl> getAttributes() {
    return myAttributes;
  }

  Map<JavaMethodSignature, Pair<FixedChildDescriptionImpl, Integer>> getFixedGetters() {
    final Map<JavaMethodSignature, Pair<FixedChildDescriptionImpl, Integer>> map = new HashMap<>();
    final Set<XmlName> names = myFixedChildrenGetters.keySet();
    for (final XmlName name : names) {
      Int2ObjectMap<Collection<JavaMethod>> map1 = myFixedChildrenGetters.get(name);
      int max = 0;
      int[] ints = map1.keySet().toIntArray();
      for (final int i : ints) {
        max = Math.max(max, i);
      }
      int count = max + 1;
      final Collection<JavaMethod>[] getters = new Collection[count];
      for (final int i : ints) {
        getters[i] = map1.get(i);
      }
      final FixedChildDescriptionImpl description = new FixedChildDescriptionImpl(name, map1.get(0).iterator().next().getGenericReturnType(), count, getters);
      for (int i = 0; i < getters.length; i++) {
        final Collection<JavaMethod> collection = getters[i];
        for (final JavaMethod method : collection) {
          map.put(method.getSignature(), Pair.create(description, i));
        }
      }
    }
    return map;
  }

  Map<JavaMethodSignature, CollectionChildDescriptionImpl> getCollectionGetters() {
    final Map<JavaMethodSignature, CollectionChildDescriptionImpl> getters = new HashMap<>();
    for (final XmlName xmlName : myCollectionGetters.keySet()) {
      final Collection<JavaMethod> collGetters = myCollectionGetters.get(xmlName);
      final JavaMethod method = collGetters.iterator().next();


      final CollectionChildDescriptionImpl description = new CollectionChildDescriptionImpl(xmlName, DomReflectionUtil.extractCollectionElementType(method.getGenericReturnType()),
                                                                                            collGetters
      );
      for (final JavaMethod getter : collGetters) {
        getters.put(getter.getSignature(), description);
      }
    }

    return getters;
  }

  Map<JavaMethodSignature, Pair<String, String[]>> getCompositeCollectionAdders() {
    return myCompositeCollectionAdders;
  }

  Map<JavaMethodSignature, String[]> getCompositeCollectionGetters() {
    return myCompositeCollectionGetters;
  }

  public JavaMethod getNameValueGetter() {
    return myNameValueGetter;
  }

  public boolean isValueElement() {
    return myValueElement;
  }
}
