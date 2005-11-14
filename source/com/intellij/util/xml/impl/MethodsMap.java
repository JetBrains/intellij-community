/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomMethodsInfo;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author peter
 */
public class MethodsMap implements DomMethodsInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.MethodsMap");

  private Class<? extends DomElement> myClass;
  private Map<Method, Pair<String, Integer>> myFixedChildrenMethods;
  private Map<String, Integer> myFixedChildrenCounts;
  private Map<Method, String> myCollectionChildrenGetterMethods;
  private Map<Method, String> myCollectionChildrenAdditionMethods;
  private Map<String, Type> myCollectionChildrenClasses;

  public MethodsMap(final Class<? extends DomElement> aClass) {
    myClass = aClass;
  }

  final int getFixedChildrenCount(String qname) {
    final Integer integer = myFixedChildrenCounts.get(qname);
    return integer == null ? 0 : (integer);
  }

  public Set<Map.Entry<Method, String>> getCollectionChildrenEntries() {
    return myCollectionChildrenGetterMethods.entrySet();
  }

  Type getCollectionChildrenType(String tagName) {
    return myCollectionChildrenClasses.get(tagName);
  }

  public Set<Map.Entry<Method, Pair<String, Integer>>> getFixedChildrenEntries() {
    return myFixedChildrenMethods.entrySet();
  }

  Pair<String, Integer> getFixedChildInfo(Method method) {
    return myFixedChildrenMethods.get(method);
  }

  private boolean isCoreMethod(final Method method) {
    final Class<?> declaringClass = method.getDeclaringClass();
    return Object.class.equals(declaringClass) || DomElement.class.equals(declaringClass);
  }

  @Nullable
  private String getSubTagName(final Method method) {
    final SubTag subTagAnnotation = method.getAnnotation(SubTag.class);
    if (subTagAnnotation == null || StringUtil.isEmpty(subTagAnnotation.value())) {
      return getNameFromMethod(method);
    }
    return subTagAnnotation.value();
  }

  @Nullable
  private String getSubTagNameForCollection(final Method method) {
    final SubTagList subTagList = method.getAnnotation(SubTagList.class);
    if (subTagList == null || StringUtil.isEmpty(subTagList.value())) {
      final String propertyName = getPropertyName(method);
      return propertyName != null ? getNameStrategy().convertName(StringUtil.unpluralize(propertyName)) : null;
    }
    return subTagList.value();
  }

  @Nullable
  private String getNameFromMethod(final Method method) {
    final String propertyName = getPropertyName(method);
    return propertyName == null ? null : getNameStrategy().convertName(propertyName);
  }

  private static String getPropertyName(Method method) {
    return PropertyUtil.getPropertyName(method.getName());
  }

  @NotNull
  private DomNameStrategy getNameStrategy() {
    final DomNameStrategy strategy = DomUtil.getDomNameStrategy(DomUtil.getRawType(myClass));
    return strategy == null ? DomNameStrategy.HYPHEN_STRATEGY : strategy;
  }

  public synchronized void buildMethodMaps(final XmlFile file) {
    if (myFixedChildrenMethods != null) return;

    myFixedChildrenMethods = new HashMap<Method, Pair<String, Integer>>();
    myFixedChildrenCounts = new HashMap<String, Integer>();

    myCollectionChildrenGetterMethods = new HashMap<Method, String>();
    myCollectionChildrenAdditionMethods = new HashMap<Method, String>();
    myCollectionChildrenClasses = new HashMap<String, Type>();

    for (Method method : myClass.getMethods()) {
      if (!isCoreMethod(method)) {
        if (DomInvocationHandler.isGetter(method)) {
          processGetterMethod(method);
        }
      }
    }
    for (Method method : myClass.getMethods()) {
      if (!isCoreMethod(method)) {
        if (isAddMethod(method)) {
          myCollectionChildrenAdditionMethods.put(method, extractTagName(method, "add"));
        }
      }
    }
  }

  private boolean isAddMethod(Method method) {
    final String tagName = extractTagName(method, "add");
    if (tagName == null) return false;

    final Type childrenClass = getCollectionChildrenType(tagName);
    if (childrenClass == null || !DomUtil.getRawType(childrenClass).isAssignableFrom(method.getReturnType())) return false;

    final Class<?>[] parameterTypes = method.getParameterTypes();
    if (parameterTypes.length > 1) return false;
    return parameterTypes.length == 0 || parameterTypes[0] == int.class;
  }

  private String extractTagName(Method method, String prefix) {
    final String name = method.getName();
    if (!name.startsWith(prefix)) return null;

    final SubTagList subTagAnnotation = method.getAnnotation(SubTagList.class);
    if (subTagAnnotation != null && !StringUtil.isEmpty(subTagAnnotation.value())) {
      return subTagAnnotation.value();
    }

    final String tagName = getNameStrategy().convertName(name.substring(prefix.length()));
    return StringUtil.isEmpty(tagName) ? null : tagName;
  }

  private void processGetterMethod(final Method method) {
    if (DomUtil.isDomElement(method.getReturnType())) {
      final String qname = getSubTagName(method);
      if (qname != null) {
        int index = 0;
        final SubTag subTagAnnotation = method.getAnnotation(SubTag.class);
        if (subTagAnnotation != null && subTagAnnotation.index() != 0) {
          index = subTagAnnotation.index();
        }
        myFixedChildrenMethods.put(method, new Pair<String, Integer>(qname, index));
        final Integer integer = myFixedChildrenCounts.get(qname);
        if (integer == null || integer < index + 1) {
          myFixedChildrenCounts.put(qname, index + 1);
        }
        return;
      }
    }

    final Type type = DomUtil.extractCollectionElementType(method.getGenericReturnType());
    if (type != null) {
      final String qname = getSubTagNameForCollection(method);
      if (qname != null) {
        myCollectionChildrenClasses.put(qname, type);
        myCollectionChildrenGetterMethods.put(method, qname);
      }
    }
  }

  public Invocation createInvocation(final XmlFile file, final Method method) {
    buildMethodMaps(file);

    if (myFixedChildrenMethods.containsKey(method)) {
      return new GetFixedChildInvocation(method);
    }

    String qname = myCollectionChildrenGetterMethods.get(method);
    if (qname != null) {
      return new GetCollectionChildInvocation(qname, getFixedChildrenCount(qname));
    }

    qname = myCollectionChildrenAdditionMethods.get(method);
    if (qname != null) {
      return new AddChildInvocation(method.getGenericReturnType(), qname, getFixedChildrenCount(qname));
    }

    throw new UnsupportedOperationException("No implementation for method " + method.toString());
  }

  public Collection<Method> getFixedChildrenGetterMethods() {
    return Collections.unmodifiableCollection(myFixedChildrenMethods.keySet());
  }

  public Collection<Method> getCollectionChildrenGetterMethods() {
    return Collections.unmodifiableCollection(myCollectionChildrenGetterMethods.keySet());
  }

  public int getFixedChildIndex(Method method) {
    final Pair<String, Integer> pair = myFixedChildrenMethods.get(method);
    LOG.assertTrue(pair != null, "Should be fixed child getter method: " + method);
    return pair.getSecond();
  }

  public String getTagName(Method method) {
    final Pair<String, Integer> pair = myFixedChildrenMethods.get(method);
    return pair != null ? pair.getFirst() : myCollectionChildrenGetterMethods.get(method);
  }

  public Method getCollectionGetMethod(String tagName) {
    for (Map.Entry<Method, String> entry : myCollectionChildrenGetterMethods.entrySet()) {
      if (tagName.equals(entry.getValue())) {
        return entry.getKey();
      }
    }
    return null;
  }

  public Method getCollectionAddMethod(String tagName) {
    for (Map.Entry<Method, String> entry : myCollectionChildrenAdditionMethods.entrySet()) {
      if (tagName.equals(entry.getValue())) {
        final Method method = entry.getKey();
        if (method.getParameterTypes().length == 0) {
          return method;
        }
      }
    }
    return null;
  }

  public Method getCollectionIndexedAddMethod(String tagName) {
    for (Map.Entry<Method, String> entry : myCollectionChildrenAdditionMethods.entrySet()) {
      if (tagName.equals(entry.getValue())) {
        final Method method = entry.getKey();
        if (method.getParameterTypes().length == 1) {
          return method;
        }
      }
    }
    return null;
  }

  public Method[] getFixedChildrenGetterMethods(String tagName) {
    final Method[] methods = new Method[getFixedChildrenCount(tagName)];
    for (Map.Entry<Method, Pair<String, Integer>> entry : myFixedChildrenMethods.entrySet()) {
      final Pair<String, Integer> pair = entry.getValue();
      if (tagName.equals(pair.getFirst())) {
        methods[pair.getSecond()] = entry.getKey();
      }
    }
    return methods;
  }

  @NotNull
  public List<DomChildrenDescription> getChildrenDescriptions() {
    final ArrayList<DomChildrenDescription> result = new ArrayList<DomChildrenDescription>();
    result.addAll(getFixedChildrenDescriptions());
    result.addAll(getCollectionChildrenDescriptions());
    return result;
  }

  @NotNull
  public List<DomFixedChildDescription> getFixedChildrenDescriptions() {
    final ArrayList<DomFixedChildDescription> result = new ArrayList<DomFixedChildDescription>();
    for (String s : myFixedChildrenCounts.keySet()) {
      result.add(getFixedChildDescription(s));
    }
    return result;
  }

  @NotNull
  public List<DomCollectionChildDescription> getCollectionChildrenDescriptions() {
    final ArrayList<DomCollectionChildDescription> result = new ArrayList<DomCollectionChildDescription>();
    for (String s : myCollectionChildrenClasses.keySet()) {
      result.add(getCollectionChildDescription(s));
    }
    return result;
  }

  @Nullable
  public DomFixedChildDescription getFixedChildDescription(String tagName) {
    final Method[] getterMethods = getFixedChildrenGetterMethods(tagName);
    return new FixedChildDescriptionImpl(tagName,
                                         getterMethods[0].getGenericReturnType(),
                                         getFixedChildrenCount(tagName),
                                         getterMethods);
  }

  @Nullable
  public DomCollectionChildDescription getCollectionChildDescription(String tagName) {
    return new CollectionChildDescriptionImpl(tagName,
                                              getCollectionChildrenType(tagName),
                                              getCollectionGetMethod(tagName),
                                              getCollectionAddMethod(tagName),
                                              getCollectionIndexedAddMethod(tagName),
                                              getFixedChildrenCount(tagName));
  }
}
