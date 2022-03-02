// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class DomReflectionUtil {
  private DomReflectionUtil() {
  }

  public static <T extends Annotation> T findAnnotationDFS(@NotNull Class<?> rawType, Class<T> annotationType) {
    T annotation = rawType.getAnnotation(annotationType);
    if (annotation != null) {
      return annotation;
    }

    for (Class<?> aClass : rawType.getInterfaces()) {
      annotation = findAnnotationDFS(aClass, annotationType);
      if (annotation != null) {
        return annotation;
      }
    }
    return null;
  }

  public static boolean canHaveIsPropertyGetterPrefix(Type type) {
    return boolean.class.equals(type) || Boolean.class.equals(type)
           || Boolean.class.equals(DomUtil.getGenericValueParameter(type));
  }

  public static JavaMethod[] getGetterMethods(String[] path, Class<? extends DomElement> startClass) {
    final JavaMethod[] methods = new JavaMethod[path.length];
    Class<?> aClass = startClass;
    for (int i = 0; i < path.length; i++) {
      JavaMethod getter = findGetter(aClass, path[i]);
      assert getter != null : "Couldn't find getter for property " + path[i] + " in class " + aClass;
      methods[i] = getter;
      aClass = getter.getReturnType();
      if (List.class.isAssignableFrom(aClass)) {
        aClass = ReflectionUtil.getRawType(Objects.requireNonNull(extractCollectionElementType(getter.getGenericReturnType())));
      }
    }
    return methods;
  }

  @Nullable
  public static JavaMethod findGetter(Class<?> aClass, String propertyName) {
    final String capitalized = StringUtil.capitalize(propertyName);
    Method method = ReflectionUtil.getMethod(aClass, "get" + capitalized);
    if (method != null) return JavaMethod.getMethod(aClass, method);

    method = ReflectionUtil.getMethod(aClass, "is" + capitalized);
    if (method == null) return null;

    final JavaMethod javaMethod = JavaMethod.getMethod(aClass, method);
    return canHaveIsPropertyGetterPrefix(javaMethod.getGenericReturnType()) ? javaMethod : null;
  }

  public static Object invokeMethod(Method method, Object object, Object... args) {
    try {
      return method.invoke(object, args);
    }
    catch (IllegalArgumentException e) {
      throw new RuntimeException("Calling method " + method + " on object " + object + " with arguments " + Arrays.asList(args), e);
    }
    catch (InvocationTargetException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof ProcessCanceledException) {
        throw (ProcessCanceledException)cause;
      }
      ExceptionUtil.rethrow(cause);
      return null;
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

  public static @Nullable Type extractCollectionElementType(Type returnType) {
    if (!(returnType instanceof ParameterizedType)) {
      return null;
    }

    ParameterizedType parameterizedType = (ParameterizedType)returnType;
    Type rawType = parameterizedType.getRawType();
    if (!(rawType instanceof Class)) {
      return null;
    }

    Class<?> rawClass = (Class<?>)rawType;
    if (!List.class.equals(rawClass) && !Collection.class.equals(rawClass)) {
      return null;
    }

    final Type[] arguments = ReflectionUtil.getActualTypeArguments(parameterizedType);
    if (arguments.length == 1) {
      final Type argument = arguments[0];
      if (argument instanceof WildcardType) {
        final Type[] upperBounds = ((WildcardType)argument).getUpperBounds();
        if (upperBounds.length == 1) {
          return upperBounds[0];
        }
      }
      else if (argument instanceof ParameterizedType) {
        if (DomUtil.getGenericValueParameter(argument) != null) {
          return argument;
        }
      }
      else if (argument instanceof Class) {
        return argument;
      }
    }
    return null;
  }
}
