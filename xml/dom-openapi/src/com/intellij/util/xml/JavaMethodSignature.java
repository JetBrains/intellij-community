/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.util.*;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    if (method == null && aClass.isInterface()) {
      method = getDeclaredMethod(Object.class);
    }
    return method;
  }

  private boolean processMethods(final Class aClass, Processor<Method> processor) {
    return processMethodWithSupers(aClass, findMethod(aClass), processor);
  }

  @Nullable
  private Method getDeclaredMethod(final Class aClass) {
    final Method method = ReflectionUtil.getMethod(aClass, myMethodName, myMethodParameters);
    return method == null ? ReflectionUtil.getDeclaredMethod(aClass, myMethodName, myMethodParameters) : method;
  }

  private boolean processMethodWithSupers(final Class aClass, final Method method, final Processor<Method> processor) {
    if (method != null) {
      if (!processor.process(method)) return false;
    }
    final Class superClass = aClass.getSuperclass();
    if (superClass != null) {
      if (!processMethods(superClass, processor)) return false;
    }
    else {
      if (aClass.isInterface()) {
        if (!processMethods(Object.class, processor)) return false;
      }
    }
    for (final Class anInterface : aClass.getInterfaces()) {
      if (!processMethods(anInterface, processor)) return false;
    }
    return true;
  }

  public final List<Method> getAllMethods(final Class startFrom) {
    final List<Method> result = new ArrayList<>();
    processMethods(startFrom, Processors.cancelableCollectProcessor(result));
    return result;
  }

  @Nullable
  public final <T extends Annotation> Method findAnnotatedMethod(final Class<T> annotationClass, final Class startFrom) {
    CommonProcessors.FindFirstProcessor<Method> processor = new CommonProcessors.FindFirstProcessor<Method>() {
      @Override
      protected boolean accept(Method method) {
        final T annotation = method.getAnnotation(annotationClass);
        return annotation != null && ReflectionUtil.isAssignable(method.getDeclaringClass(), startFrom);
      }
    };
    processMethods(startFrom, processor);
    return processor.getFoundValue();
  }

  @Nullable
  public final <T extends Annotation> T findAnnotation(final Class<T> annotationClass, final Class startFrom) {
    CommonProcessors.FindFirstProcessor<Method> processor = new CommonProcessors.FindFirstProcessor<Method>() {
      @Override
      protected boolean accept(Method method) {
        final T annotation = method.getAnnotation(annotationClass);
        return annotation != null;
      }
    };
    processMethods(startFrom, processor);
    final Method foundMethod = processor.getFoundValue();
    return foundMethod == null ? null : foundMethod.getAnnotation(annotationClass);
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
