/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.xml.XmlFile;
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
  private Map<Method, Pair<String, Class<? extends DomElement>>> myVariableChildrenMethods;

  public MethodsMap(final Class<? extends DomElement> aClass) {
    myClass = aClass;
  }

  boolean isVariableChildrenMethod(final Method method) {
    return myVariableChildrenMethods.containsKey(method);
  }

  String getVariableChildrenTagQName(final Method method) {
    return myVariableChildrenMethods.get(method).getFirst();
  }

  Pair<String, Integer> getFixedChildInfo(Method method) {
    return myFixedChildrenMethods.get(method);
  }

  int getFixedChildrenCount(String qname) {
    final Integer integer = myFixedChildrenCounts.get(qname);
    return integer == null ? 0 : (integer);
  }

  Set<Map.Entry<Method, Pair<String, Class<? extends DomElement>>>> getVariableChildrenEntries() {
    return myVariableChildrenMethods.entrySet();
  }

  Set<Map.Entry<Method, Pair<String, Integer>>> getFixedChildrenEntries() {
    return myFixedChildrenMethods.entrySet();
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
              WildcardType wildcardType = (WildcardType)argument;
              final Type[] upperBounds = wildcardType.getUpperBounds();
              if (upperBounds.length == 1) {
                final Type upperBound = upperBounds[0];
                if (upperBound instanceof Class) {
                  Class aClass1 = (Class)upperBound;
                  if (DomElement.class.isAssignableFrom(aClass1)) {
                    return (Class<? extends DomElement>)aClass1;
                  }
                }
              }
            }
            else if (argument instanceof Class) {
              Class aClass1 = (Class)argument;
              if (DomElement.class.isAssignableFrom(aClass1)) {
                return (Class<? extends DomElement>)aClass1;
              }
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


  synchronized void buildMethodMaps(final XmlFile file) {
    if (myFixedChildrenMethods != null) return;
    myFixedChildrenMethods = new HashMap<Method, Pair<String, Integer>>();
    myVariableChildrenMethods = new HashMap<Method, Pair<String, Class<? extends DomElement>>>();
    myFixedChildrenCounts = new HashMap<String, Integer>();

    for (Method method : myClass.getMethods()) {
      if (!isCoreMethod(method)) {
        final Class<?> returnType = method.getReturnType();
        if (DomElement.class.isAssignableFrom(returnType)) {
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
            myVariableChildrenMethods.put(method, new Pair<String, Class<? extends DomElement>>(qname, aClass));
          }
        }
      }
    }
  }

  public boolean isFixedChildrenMethod(final Method method) {
    return myFixedChildrenMethods.containsKey(method);
  }
}
