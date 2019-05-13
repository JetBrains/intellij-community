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
package com.intellij.util.xml;

import com.intellij.util.SmartFMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

/**
 * @author peter
 */
public final class JavaMethod implements AnnotatedElement {
  public static final JavaMethod[] EMPTY_ARRAY = new JavaMethod[0];
  private static final Object NONE = new Object();

  private final JavaMethodSignature mySignature;
  private final Class myDeclaringClass;
  private final Method myMethod;
  private volatile SmartFMap<Class, Object> myAnnotationsMap = SmartFMap.emptyMap();

  private JavaMethod(final Class declaringClass, final JavaMethodSignature signature) {
    mySignature = signature;
    myMethod = signature.findMethod(declaringClass);
    assert myMethod != null : "No method " + signature + " in class " + declaringClass;
    myDeclaringClass = myMethod.getDeclaringClass();
  }

  public final Class getDeclaringClass() {
    return myDeclaringClass;
  }

  public final JavaMethodSignature getSignature() {
    return mySignature;
  }

  public final List<Method> getHierarchy() {
    return mySignature.getAllMethods(myDeclaringClass);
  }

  public String getMethodName() {
    return mySignature.getMethodName();
  }

  public final Method getMethod() {
    return myMethod;
  }

  public final Type[] getGenericParameterTypes() {
    return myMethod.getGenericParameterTypes();
  }

  public final Type getGenericReturnType() {
    return myMethod.getGenericReturnType();
  }

  public static JavaMethod getMethod(final Class declaringClass, final JavaMethodSignature signature) {
    return new JavaMethod(declaringClass, signature);
  }

  public static JavaMethod getMethod(final Class declaringClass, final Method method) {
    return getMethod(declaringClass, new JavaMethodSignature(method));
  }

  public final Object invoke(final Object o, final Object... args) {
    return DomReflectionUtil.invokeMethod(myMethod, o, args);
  }

  public String toString() {
    return "JavaMethod: " + myMethod.toString();
  }

  @NonNls
  public final String getName() {
    return myMethod.getName();
  }

  @Override
  public final <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    Object annotation = myAnnotationsMap.get(annotationClass);
    if (annotation == null) {
      myAnnotationsMap = myAnnotationsMap.plus(annotationClass, annotation = findAnnotation(annotationClass));
    }
    //noinspection unchecked
    return annotation == NONE ? null : (T)annotation;
  }

  @NotNull
  private Object findAnnotation(Class<? extends Annotation> annotationClass) {
    final Annotation annotation = mySignature.findAnnotation(annotationClass, myDeclaringClass);
    return annotation == null ? NONE : annotation;
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

  public final Class getReturnType() {
    return myMethod.getReturnType();
  }

  public Class<?>[] getParameterTypes() {
    return myMethod.getParameterTypes();
  }
}
