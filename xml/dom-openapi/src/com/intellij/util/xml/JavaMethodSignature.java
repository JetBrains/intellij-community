/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionCache;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author peter
 */
public class JavaMethodSignature {
  private final String myMethodName;
  private final Class[] myMethodParameters;

  public JavaMethodSignature(final String methodName, final Class... methodParameters) {
    myMethodName = methodName;
    myMethodParameters = methodParameters.length == 0 ? ArrayUtil.EMPTY_CLASS_ARRAY : methodParameters;
  }

  public JavaMethodSignature(Method method) {
    this(method.getName(), method.getParameterTypes());
  }

  public String getMethodName() {
    return myMethodName;
  }

  @Nullable
  public final Method findMethod(final Class aClass) {
    Method method = getDeclaredMethod(aClass);
    if (method == null && ReflectionCache.isInterface(aClass)) {
      method = getDeclaredMethod(Object.class);
    }
    return method;
  }

  private void collectMethods(final Class aClass, List<Method> methods) {
    addMethodWithSupers(aClass, findMethod(aClass), methods);
  }

  @Nullable
  private Method getDeclaredMethod(final Class aClass) {
    final Method method = ReflectionUtil.getMethod(aClass, myMethodName, myMethodParameters);
    return method == null ? ReflectionUtil.getDeclaredMethod(aClass, myMethodName, myMethodParameters) : method;
  }

  private void addMethodWithSupers(final Class aClass, final Method method, List<Method> methods) {
    if (method != null) {
      methods.add(method);
    }
    final Class superClass = aClass.getSuperclass();
    if (superClass != null) {
      collectMethods(superClass, methods);
    } else {
      if (aClass.isInterface()) {
        collectMethods(Object.class, methods);
      }
    }
    for (final Class anInterface : aClass.getInterfaces()) {
      collectMethods(anInterface, methods);
    }
  }

  public final List<Method> getAllMethods(final Class startFrom) {
    final ArrayList<Method> methods = new ArrayList<Method>();
    collectMethods(startFrom, methods);
    return methods;
  }

  @Nullable
  public final <T extends Annotation> Method findAnnotatedMethod(final Class<T> annotationClass, final Class startFrom) {
    for (Method method : getAllMethods(startFrom)) {
      final T annotation = method.getAnnotation(annotationClass);
      if (annotation != null && ReflectionCache.isAssignable(method.getDeclaringClass(), startFrom)) {
        return method;
      }
    }
    return null;
  }

  public String toString() {
    return myMethodName + Arrays.asList(myMethodParameters);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final JavaMethodSignature that = (JavaMethodSignature)o;

    if (!myMethodName.equals(that.myMethodName)) return false;
    if (!Arrays.equals(myMethodParameters, that.myMethodParameters)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myMethodName.hashCode();
    result = 31 * result + Arrays.hashCode(myMethodParameters);
    return result;
  }

}
