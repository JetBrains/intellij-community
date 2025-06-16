// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import com.intellij.util.ArrayUtil;
import com.intellij.util.JBIterableClassTraverser;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class JavaMethodSignature {
  private static final Set<String> OBJECT_METHOD_NAMES = ContainerUtil.map2Set(Object.class.getDeclaredMethods(), Method::getName);
  private final String myMethodName;
  private final Class<?>[] myMethodParameters;

  public JavaMethodSignature(String methodName, Class<?>... methodParameters) {
    myMethodName = methodName;
    myMethodParameters = methodParameters.length == 0 ? ArrayUtil.EMPTY_CLASS_ARRAY : methodParameters;
  }

  public JavaMethodSignature(@NotNull Method method) {
    myMethodName = method.getName();
    myMethodParameters = method.getParameterCount() == 0 ? ArrayUtil.EMPTY_CLASS_ARRAY : method.getParameterTypes();
  }

  public String getMethodName() {
    return myMethodName;
  }

  public @Nullable Method findMethod(@NotNull Class<?> aClass) {
    Method method = getDeclaredMethod(aClass);
    if (method == null && aClass.isInterface() && OBJECT_METHOD_NAMES.contains(myMethodName)) {
      method = ReflectionUtil.getDeclaredMethod(Object.class, myMethodName, myMethodParameters);
    }
    return method;
  }

  private @Nullable Method getDeclaredMethod(@NotNull Class<?> aClass) {
    Method method = ReflectionUtil.getMethod(aClass, myMethodName, myMethodParameters);
    return method == null ? ReflectionUtil.getDeclaredMethod(aClass, myMethodName, myMethodParameters) : method;
  }

  @NotNull
  List<Method> getAllMethods(@NotNull Class<?> startFrom) {
    List<Method> result = new ArrayList<>();
    for (Class<?> superClass : JBIterable.from(JBIterableClassTraverser.classTraverser(startFrom)).append(Object.class).unique()) {
      for (Method method : superClass.getDeclaredMethods()) {
        if (myMethodName.equals(method.getName()) &&
            method.getParameterCount() == myMethodParameters.length &&
            Arrays.equals(method.getParameterTypes(), myMethodParameters)) {
          result.add(method);
        }
      }
    }
    return result;
  }

  @ApiStatus.Internal
  public static @Nullable Method findMethod(@NotNull Method sampleMethod, @NotNull Class<?> startFrom, @NotNull Predicate<Method> checker) {
    String sampleMethodName = sampleMethod.getName();
    Class<?>[] sampleMethodParameters = sampleMethod.getParameterCount() == 0 ? ArrayUtil.EMPTY_CLASS_ARRAY : sampleMethod.getParameterTypes();

    for (Class<?> superClass : JBIterable.from(JBIterableClassTraverser.classTraverser(startFrom)).append(Object.class).unique()) {
      for (Method method : superClass.getDeclaredMethods()) {
        if (sampleMethodName.equals(method.getName()) &&
            method.getParameterCount() == sampleMethodParameters.length &&
            Arrays.equals(method.getParameterTypes(), sampleMethodParameters)) {
          if (checker.test(method)) {
            return method;
          }
        }
      }
    }

    return null;
  }

  @Override
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
