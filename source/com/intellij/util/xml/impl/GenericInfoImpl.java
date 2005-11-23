/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author peter
 */
public class GenericInfoImpl implements DomGenericInfo {
  private final Class<? extends DomElement> myClass;
  private final Map<Method, Pair<String, Integer>> myFixedChildrenMethods = new HashMap<Method, Pair<String, Integer>>();
  private final Map<String, Integer> myFixedChildrenCounts = new HashMap<String, Integer>();
  private final Map<Method, String> myCollectionChildrenGetterMethods = new HashMap<Method, String>();
  private final Map<Method, String> myCollectionChildrenAdditionMethods = new HashMap<Method, String>();
  private final Map<String, Type> myCollectionChildrenClasses = new HashMap<String, Type>();
  private final Map<Method, String> myAttributeChildrenMethods = new HashMap<Method, String>();
  private final Map<Class, Class> myImplementationClasses = new HashMap<Class, Class>();
  private boolean myValueElement;
  private boolean myInitialized;
  private static final HashSet ADDER_PARAMETER_TYPES = new HashSet<Class>(Arrays.asList(Class.class, int.class));


  public GenericInfoImpl(final Class<? extends DomElement> aClass) {
    myClass = aClass;
  }

  final int getFixedChildrenCount(String qname) {
    final Integer integer = myFixedChildrenCounts.get(qname);
    return integer == null ? 0 : (integer);
  }

  final Set<Map.Entry<Method, String>> getCollectionChildrenEntries() {
    return myCollectionChildrenGetterMethods.entrySet();
  }

  final Type getCollectionChildrenType(String tagName) {
    return myCollectionChildrenClasses.get(tagName);
  }

  final Set<Map.Entry<Method, String>> getAttributeChildrenEntries() {
    return myAttributeChildrenMethods.entrySet();
  }

  final Set<Map.Entry<Method, Pair<String, Integer>>> getFixedChildrenEntries() {
    return myFixedChildrenMethods.entrySet();
  }

  final Pair<String, Integer> getFixedChildInfo(Method method) {
    return myFixedChildrenMethods.get(method);
  }

  final String getAttributeName(Method method) {
    return myAttributeChildrenMethods.get(method);
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

  private void collectImplementations(Class<?> interfaceClass) {
    final Implementation annotation = interfaceClass.getAnnotation(Implementation.class);
    if (annotation != null) {
      myImplementationClasses.put(annotation.value(), interfaceClass);
    }
    for (Class aClass1 : interfaceClass.getInterfaces()) {
      collectImplementations(aClass1);
    }
  }

  public final synchronized void buildMethodMaps() {
    if (myInitialized) return;

    myInitialized = true;

    collectImplementations(myClass);

    for (Method method : myClass.getMethods()) {
      if (!isCoreMethod(method)) {
        if (DomUtil.isGetter(method)) {
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

    return ADDER_PARAMETER_TYPES.containsAll(Arrays.asList(method.getParameterTypes()));
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
    if (method.getAnnotation(PropertyAccessor.class) != null || method.getAnnotation(CustomMethod.class) != null) {
      return;
    }

    if (DomUtil.isTagValueGetter(method)) {
      myValueElement = true;
      return;
    }

    final boolean isAttributeMethod = method.getReturnType().equals(GenericAttributeValue.class);
    final Attribute annotation = method.getAnnotation(Attribute.class);
    if (annotation != null) {
      assert isAttributeMethod : method + " should return " + GenericAttributeValue.class;
    }
    if (isAttributeMethod) {
      final String s = annotation == null ? null : annotation.value();
      String attributeName = StringUtil.isEmpty(s) ? getNameFromMethod(method): s;
      assert StringUtil.isNotEmpty(attributeName) : "Can't guess attribute name from method name: " + method.getName();
      myAttributeChildrenMethods.put(method, attributeName);
      return;
    }

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

  public final Invocation createInvocation(final XmlFile file, final Method method) {
    buildMethodMaps();

    final CustomMethod customMethod = method.getAnnotation(CustomMethod.class);
    if (customMethod != null) {
      return createCustomMethodInvocation(customMethod, method);
    }

    final PropertyAccessor accessor = method.getAnnotation(PropertyAccessor.class);
    if (accessor != null) {
      return createPropertyAccessorInvocation(accessor);
    }

    if (myAttributeChildrenMethods.containsKey(method)) {
      return new GetAttributeChildInvocation(method);
    }

    if (myFixedChildrenMethods.containsKey(method)) {
      return new GetFixedChildInvocation(method);
    }

    String qname = myCollectionChildrenGetterMethods.get(method);
    if (qname != null) {
      return new GetCollectionChildInvocation(qname, getFixedChildrenCount(qname));
    }

    qname = myCollectionChildrenAdditionMethods.get(method);
    if (qname != null) {
      return new AddChildInvocation(getTypeGetter(method),
                                    getIndexGetter(method, getFixedChildrenCount(qname)),
                                    qname,
                                    myCollectionChildrenClasses.get(qname));
    }

    throw new UnsupportedOperationException("No implementation for method " + method.toString());
  }

  private Invocation createPropertyAccessorInvocation(final PropertyAccessor accessor) {
    final String[] names = accessor.value();
    final Method[] methods = new Method[names.length];
    Class aClass = myClass;
    for (int i = 0; i < names.length; i++) {
      methods[i] = findGetter(aClass, names[i]);
      aClass = methods[i].getReturnType();
    }
    return new Invocation() {
      public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
        Object object = handler.getProxy();
        for (Method method : methods) {
          object = method.invoke(object);
        }
        return object;
      }
    };
  }

  private Method findImplementationMethod(String methodName, Class[] paramTypes) {
    for (Class aClass : myImplementationClasses.keySet()) {
      try {
        return aClass.getMethod(methodName, paramTypes);
      }
      catch (NoSuchMethodException e) {
      }
    }
    return null;
  }

  private Invocation createCustomMethodInvocation(final CustomMethod customMethod, final Method method) {
    final Class aClass = customMethod.value();
    final String methodName = StringUtil.isNotEmpty(customMethod.methodName()) ? customMethod.methodName() : method.getName();

    if (void.class.equals(aClass)) {
      final Method implementationMethod = findImplementationMethod(methodName, method.getParameterTypes());
      assert implementationMethod != null;
      final Class interfaceClass = myImplementationClasses.get(implementationMethod.getDeclaringClass());
      return new Invocation() {
        public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
          return implementationMethod.invoke(handler.getImplementation(interfaceClass), args);
        }
      };
    }

    try {
      final Class[] newParameterTypes = insertFirst(DomElement.class, (Class[])method.getParameterTypes());
      final Method staticMethod = aClass.getMethod(methodName, newParameterTypes);
      final Class<?> returnType1 = method.getReturnType();
      final Class<?> returnType2 = staticMethod.getReturnType();
      assert returnType1.isAssignableFrom(returnType2) : returnType1 + " " + returnType2;
      return new Invocation() {
        public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
          return staticMethod.invoke(null, insertFirst(handler.getProxy(), args));
        }
      };
    }
    catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }

  private Method findGetter(Class aClass, String propertyName) {
    final String capitalized = StringUtil.capitalize(propertyName);
    try {
      return aClass.getMethod("get" + capitalized);
    }
    catch (NoSuchMethodException e) {
      final Method method;
      try {
        method = aClass.getMethod("is" + capitalized);
        return DomUtil.canHaveIsPropertyGetterPrefix(method.getGenericReturnType()) ? method : null;
      }
      catch (NoSuchMethodException e1) {
        return null;
      }
    }
  }

  private <T> T[] insertFirst(final T element, final T[] parameterTypes) {
    final List<T> types = new ArrayList<T>();
    types.add(element);
    types.addAll(Arrays.asList(parameterTypes));
    return types.toArray((T[])Array.newInstance(parameterTypes.getClass().getComponentType(), types.size()));
  }

  private Function<Object[],Class> getTypeGetter(final Method method) {
    final Class<?>[] parameterTypes = method.getParameterTypes();
    if (parameterTypes.length >= 1 && parameterTypes[0].equals(Class.class)) {
      return new Function<Object[], Class>() {
        public Class fun(final Object[] s) {
          return (Class)s[0];
        }
      };
    }

    if (parameterTypes.length == 2 && parameterTypes[1].equals(Class.class)) {
      return new Function<Object[], Class>() {
        public Class fun(final Object[] s) {
          return (Class)s[1];
        }
      };
    }

    return new Function<Object[], Class>() {
      public Class fun(final Object[] s) {
        return method.getReturnType();
      }
    };
  }


  private Function<Object[],Integer> getIndexGetter(final Method method, final int startIndex) {
    final Class<?>[] parameterTypes = method.getParameterTypes();
    if (parameterTypes.length >= 1 && parameterTypes[0].equals(int.class)) {
      return new Function<Object[], Integer>() {
        public Integer fun(final Object[] s) {
          return (Integer)s[0] + startIndex;
        }
      };
    }

    if (parameterTypes.length == 2 && parameterTypes[1].equals(int.class)) {
      return new Function<Object[], Integer>() {
        public Integer fun(final Object[] s) {
          return (Integer)s[1] + startIndex;
        }
      };
    }

    return new Function<Object[], Integer>() {
      public Integer fun(final Object[] s) {
        return Integer.MAX_VALUE;
      }
    };
  }

  private Method findGetterMethod(final Map<Method, String> map, final String xmlElementName) {
    buildMethodMaps();
    for (Map.Entry<Method, String> entry : map.entrySet()) {
      if (xmlElementName.equals(entry.getValue())) {
        return entry.getKey();
      }
    }
    return null;
  }

  private Method getCollectionAddMethod(final String tagName, Class... parameterTypes) {
    for (Map.Entry<Method, String> entry : myCollectionChildrenAdditionMethods.entrySet()) {
      if (tagName.equals(entry.getValue())) {
        final Method method = entry.getKey();
        if (Arrays.equals(parameterTypes, method.getParameterTypes())) {
          return method;
        }
      }
    }
    return null;
  }

  private Method[] getFixedChildrenGetterMethods(String tagName) {
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
    result.addAll(getAttributeChildrenDescriptions());
    result.addAll(getFixedChildrenDescriptions());
    result.addAll(getCollectionChildrenDescriptions());
    return result;
  }

  @NotNull
  public List<DomFixedChildDescription> getFixedChildrenDescriptions() {
    buildMethodMaps();
    final ArrayList<DomFixedChildDescription> result = new ArrayList<DomFixedChildDescription>();
    for (String s : myFixedChildrenCounts.keySet()) {
      result.add(getFixedChildDescription(s));
    }
    return result;
  }

  @NotNull
  public List<DomCollectionChildDescription> getCollectionChildrenDescriptions() {
    buildMethodMaps();
    final ArrayList<DomCollectionChildDescription> result = new ArrayList<DomCollectionChildDescription>();
    for (String s : myCollectionChildrenClasses.keySet()) {
      result.add(getCollectionChildDescription(s));
    }
    return result;
  }

  @Nullable
  public DomFixedChildDescription getFixedChildDescription(String tagName) {
    buildMethodMaps();
    final Method[] getterMethods = getFixedChildrenGetterMethods(tagName);
    return new FixedChildDescriptionImpl(tagName,
                                         getterMethods[0].getGenericReturnType(),
                                         getFixedChildrenCount(tagName),
                                         getterMethods);
  }

  @Nullable
  public DomCollectionChildDescription getCollectionChildDescription(String tagName) {
    buildMethodMaps();
    return new CollectionChildDescriptionImpl(tagName,
                                              getCollectionChildrenType(tagName),
                                              getCollectionAddMethod(tagName),
                                              getCollectionAddMethod(tagName, Class.class),
                                              findGetterMethod(myCollectionChildrenGetterMethods, tagName),
                                              getCollectionAddMethod(tagName, int.class),
                                              getCollectionAddMethod(tagName, Class.class, int.class),
                                              getCollectionAddMethod(tagName, int.class, Class.class),
                                              getFixedChildrenCount(tagName));
  }

  @Nullable
  public DomAttributeChildDescription getAttributeChildDescription(String attributeName) {
    return new AttributeChildDescriptionImpl(attributeName, findGetterMethod(myAttributeChildrenMethods, attributeName));
  }

  public boolean isTagValueElement() {
    buildMethodMaps();
    return myValueElement;
  }

  @NotNull
  public List<DomAttributeChildDescription> getAttributeChildrenDescriptions() {
    final ArrayList<DomAttributeChildDescription> result = new ArrayList<DomAttributeChildDescription>();
    for (Map.Entry<Method, String> entry : myAttributeChildrenMethods.entrySet()) {
      result.add(new AttributeChildDescriptionImpl(entry.getValue(), entry.getKey()));
    }
    return result;
  }
}
