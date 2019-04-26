/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.*;

/**
 * @author peter
 */
public class JavaMethodSignature {
  private static final Set<String> OBJECT_METHOD_NAMES = ContainerUtil.map2Set(Object.class.getDeclaredMethods(), Method::getName);
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
    if (method == null && aClass.isInterface() && OBJECT_METHOD_NAMES.contains(myMethodName)) {
      method = ReflectionUtil.getDeclaredMethod(Object.class, myMethodName, myMethodParameters);
    }
    return method;
  }

  @Nullable
  private Method getDeclaredMethod(final Class aClass) {
    final Method method = ReflectionUtil.getMethod(aClass, myMethodName, myMethodParameters);
    return method == null ? ReflectionUtil.getDeclaredMethod(aClass, myMethodName, myMethodParameters) : method;
  }

  List<Method> getAllMethods(Class startFrom) {
    List<Method> result = new ArrayList<>();
    for (Class superClass : JBIterable.from(ReflectionUtil.classTraverser(startFrom)).append(Object.class).unique()) {
      for (Method method : superClass.getDeclaredMethods()) {
        if (methodMatches(method)) {
          result.add(method);
        }
      }
    }
    return result;
  }

  private boolean methodMatches(Method method) {
    return myMethodName.equals(method.getName()) && Arrays.equals(method.getParameterTypes(), myMethodParameters);
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
