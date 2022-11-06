// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml;

import com.intellij.util.SmartFMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

public final class JavaMethod implements AnnotatedElement {
  public static final JavaMethod[] EMPTY_ARRAY = new JavaMethod[0];
  private static final Object NONE = new Object();

  private final JavaMethodSignature mySignature;
  private final Class myDeclaringClass;
  private final Method myMethod;
  private volatile SmartFMap<Class, Object> myAnnotationsMap = SmartFMap.emptyMap();
  private volatile List<Method> myHierarchy;

  private JavaMethod(@NotNull Class<?> declaringClass, final JavaMethodSignature signature) {
    mySignature = signature;
    myMethod = signature.findMethod(declaringClass);
    assert myMethod != null : "No method " + signature + " in class " + declaringClass;
    myDeclaringClass = myMethod.getDeclaringClass();
  }

  public Class<?> getDeclaringClass() {
    return myDeclaringClass;
  }

  public JavaMethodSignature getSignature() {
    return mySignature;
  }

  @NotNull
  public List<Method> getHierarchy() {
    List<Method> hierarchy = myHierarchy;
    if (hierarchy == null) {
      hierarchy = Collections.unmodifiableList(mySignature.getAllMethods(myDeclaringClass));
      myHierarchy = hierarchy;
    }
    return hierarchy;
  }

  public String getMethodName() {
    return mySignature.getMethodName();
  }

  public Method getMethod() {
    return myMethod;
  }

  public Type[] getGenericParameterTypes() {
    return myMethod.getGenericParameterTypes();
  }

  public Type getGenericReturnType() {
    return myMethod.getGenericReturnType();
  }

  public static JavaMethod getMethod(final Class declaringClass, final JavaMethodSignature signature) {
    return new JavaMethod(declaringClass, signature);
  }

  public static JavaMethod getMethod(final Class declaringClass, final Method method) {
    return getMethod(declaringClass, new JavaMethodSignature(method));
  }

  public Object invoke(final Object o, final Object... args) {
    return DomReflectionUtil.invokeMethod(myMethod, o, args);
  }

  public String toString() {
    return "JavaMethod: " + myMethod.toString();
  }

  @NonNls
  public String getName() {
    return myMethod.getName();
  }

  @Override
  public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    Object annotation = myAnnotationsMap.get(annotationClass);
    if (annotation == null) {
      myAnnotationsMap = myAnnotationsMap.plus(annotationClass, annotation = findAnnotation(annotationClass));
    }
    //noinspection unchecked
    return annotation == NONE ? null : (T)annotation;
  }

  @NotNull
  private Object findAnnotation(Class<? extends Annotation> annotationClass) {
    for (Method method : getHierarchy()) {
      Annotation annotation = method.getAnnotation(annotationClass);
      if (annotation != null) {
        return annotation;
      }
    }
    return NONE;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof JavaMethod)) return false;

    final JavaMethod that = (JavaMethod)o;

    if (!myDeclaringClass.equals(that.myDeclaringClass)) return false;
    if (!mySignature.equals(that.mySignature)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = mySignature.hashCode();
    result = 31 * result + myDeclaringClass.hashCode();
    return result;
  }

  public Class getReturnType() {
    return myMethod.getReturnType();
  }

  public Class<?>[] getParameterTypes() {
    return myMethod.getParameterTypes();
  }

  public int getParameterCount() {
    return myMethod.getParameterCount();
  }
}
