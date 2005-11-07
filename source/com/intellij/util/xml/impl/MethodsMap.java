/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.SubTag;
import com.intellij.util.xml.SubTagList;
import com.intellij.util.xml.NameStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.*;

/**
 * @author peter
 */
public class MethodsMap {
  private Class<? extends DomElement> myClass;
  private Map<Method, Pair<String, Integer>> myFixedChildrenMethods;
  private Map<String, Integer> myFixedChildrenCounts = new HashMap<String, Integer>();
  private Map<Method, String> myCollectionChildrenGetterMethods;
  private Map<Method, String> myCollectionChildrenAdditionMethods;
  private Map<String, Class<? extends DomElement>> myCollectionChildrenClasses;

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

  Class<? extends DomElement> getCollectionChildrenClass(String tagName) {
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
  private static Class<? extends DomElement> extractElementType(Type returnType) {
    if (returnType instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType)returnType;
      final Type rawType = parameterizedType.getRawType();
      if (rawType instanceof Class) {
        final Class<?> rawClass = (Class<?>)rawType;
        if (List.class.isAssignableFrom(rawClass) || Collection.class.equals(rawClass)) {
          final Type[] arguments = parameterizedType.getActualTypeArguments();
          if (arguments.length == 1) {
            final Type argument = arguments[0];
            if (argument instanceof WildcardType) {
              final Type[] upperBounds = ((WildcardType)argument).getUpperBounds();
              if (upperBounds.length == 1 && isDomElement(upperBounds[0])) {
                return (Class<? extends DomElement>)upperBounds[0];
              }
            }
            else if (isDomElement(argument)) {
              return (Class<? extends DomElement>)argument;
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private String getSubTagName(final Method method, final XmlFile file) {
    final SubTag subTagAnnotation = method.getAnnotation(SubTag.class);
    if (subTagAnnotation == null || StringUtil.isEmpty(subTagAnnotation.value())) {
      return getNameFromMethod(method, file);
    }
    return subTagAnnotation.value();
  }

  @Nullable
  private String getSubTagNameForCollection(final Method method, final XmlFile file) {
    final SubTagList subTagList = method.getAnnotation(SubTagList.class);
    if (subTagList == null || StringUtil.isEmpty(subTagList.value())) {
      final String propertyName = getPropertyName(method);
      return propertyName != null ? getNameStrategy(file).convertName(StringUtil.unpluralize(propertyName)) : null;
    }
    return subTagList.value();
  }

  @Nullable
  private String getNameFromMethod(final Method method, final XmlFile file) {
    final String propertyName = getPropertyName(method);
    return propertyName == null ? null : getNameStrategy(file).convertName(propertyName);
  }

  private static String getPropertyName(Method method) {
    return PropertyUtil.getPropertyName(method.getName());
  }

  @NotNull
  private NameStrategy getNameStrategy(XmlFile file) {
    return DomManagerImpl._getNameStrategy(file);
  }

  public synchronized void buildMethodMaps(final XmlFile file) {
    if (myFixedChildrenMethods != null) return;
    myFixedChildrenMethods = new HashMap<Method, Pair<String, Integer>>();
    myCollectionChildrenGetterMethods = new HashMap<Method, String>();
    myFixedChildrenCounts = new HashMap<String, Integer>();
    myCollectionChildrenAdditionMethods = new HashMap<Method, String>();
    myCollectionChildrenClasses = new HashMap<String, Class<? extends DomElement>>();

    for (Method method : myClass.getMethods()) {
      if (!isCoreMethod(method)) {
        if (DomInvocationHandler.isGetter(method)) {
          processGetterMethod(method, file);
        }
      }
    }
    for (Method method : myClass.getMethods()) {
      if (!isCoreMethod(method)) {
        if (isAddMethod(method, file)) {
          myCollectionChildrenAdditionMethods.put(method, extractTagName(method, "add", file));
        }
      }
    }
  }

  private boolean isAddMethod(Method method, XmlFile file) {
    final String tagName = extractTagName(method, "add", file);
    if (tagName == null) return false;

    final Class<? extends DomElement> childrenClass = getCollectionChildrenClass(tagName);
    if (childrenClass == null || !childrenClass.isAssignableFrom(method.getReturnType())) return false;

    final Class<?>[] parameterTypes = method.getParameterTypes();
    if (parameterTypes.length > 1) return false;
    return parameterTypes.length == 0 || parameterTypes[0] == int.class;
  }

  private String extractTagName(Method method, String prefix, XmlFile file) {
    final String name = method.getName();
    if (!name.startsWith(prefix)) return null;

    final SubTagList subTagAnnotation = method.getAnnotation(SubTagList.class);
    if (subTagAnnotation != null && !StringUtil.isEmpty(subTagAnnotation.value())) {
      return subTagAnnotation.value();
    }

    final String tagName = getNameStrategy(file).convertName(name.substring(prefix.length()));
    return StringUtil.isEmpty(tagName) ? null : tagName;
  }

  private void processGetterMethod(final Method method, final XmlFile file) {
    final Class<?> returnType = method.getReturnType();
    if (isDomElement(returnType)) {
      final String qname = getSubTagName(method, file);
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
      }
    }
    final Class<? extends DomElement> aClass = extractElementType(method.getGenericReturnType());
    if (aClass != null) {
      final String qname = getSubTagNameForCollection(method, file);
      if (qname != null) {
        myCollectionChildrenClasses.put(qname, aClass);
        myCollectionChildrenGetterMethods.put(method, qname);
      }
    }
  }

  private static boolean isDomElement(final Type type) {
    return type instanceof Class && isDomElement((Class)type);
  }

  private static boolean isDomElement(final Class type) {
    return DomElement.class.isAssignableFrom(type);
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
      return new AddChildInvocation(method.getReturnType(), qname, getFixedChildrenCount(qname));
    }

    throw new UnsupportedOperationException("No implementation for method " + method.toString());
  }
}
