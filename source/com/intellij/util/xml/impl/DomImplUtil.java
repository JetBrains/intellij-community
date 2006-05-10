/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.List;
import java.util.Collection;

/**
 * @author peter
 */
public class DomImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.DomImplUtil");

  public static boolean tryAccept(final DomElementVisitor visitor, final Class aClass, DomElement proxy) {
    try {
      tryInvoke(visitor, "visit" + aClass.getSimpleName(), aClass, proxy);
      return true;
    }
    catch (NoSuchMethodException e) {
      try {
        tryInvoke(visitor, "visit", aClass, proxy);
        return true;
      }
      catch (NoSuchMethodException e1) {
        for (Class aClass1 : aClass.getInterfaces()) {
          if (tryAccept(visitor, aClass1, proxy)) {
            return true;
          }
        }
        return false;
      }
    }
  }

  static void tryInvoke(final DomElementVisitor visitor, @NonNls final String name, final Class aClass, DomElement proxy) throws NoSuchMethodException {
    try {
      final Method method = visitor.getClass().getMethod(name, aClass);
      method.setAccessible(true);
      method.invoke(visitor, proxy);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    catch (InvocationTargetException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof ProcessCanceledException) {
        throw (ProcessCanceledException)cause;
      }
      LOG.error(cause);
    }
  }

  @Nullable
  public static Type extractCollectionElementType(Type returnType) {
    if (returnType instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType)returnType;
      final Type rawType = parameterizedType.getRawType();
      if (rawType instanceof Class) {
        final Class<?> rawClass = (Class<?>)rawType;
        if (List.class.equals(rawClass) || Collection.class.equals(rawClass)) {
          final Type[] arguments = parameterizedType.getActualTypeArguments();
          if (arguments.length == 1) {
            final Type argument = arguments[0];
            if (argument instanceof WildcardType) {
              final Type[] upperBounds = ((WildcardType)argument).getUpperBounds();
              if (upperBounds.length == 1) {
                return upperBounds[0];
              }
            }
            else if (argument instanceof ParameterizedType) {
              if (DomUtil.getGenericValueType(argument) != null) {
                return argument;
              }
            }
            else if (argument instanceof Class) {
              return argument;
            }
          }
        }
      }
    }
    return null;
  }

  public static boolean isTagValueGetter(final Method method) {
    if (!isGetter(method)) {
      return false;
    }
    if (hasTagValueAnnotation(method)) {
      return true;
    }
    if ("getValue".equals(method.getName())) {
      final JavaMethodSignature signature = JavaMethodSignature.getSignature(method);
      final Class<?> declaringClass = method.getDeclaringClass();
      if (signature.findAnnotation(SubTag.class, declaringClass) != null) return false;
      if (signature.findAnnotation(SubTagList.class, declaringClass) != null) return false;
      if (DomElement.class.isAssignableFrom(method.getReturnType())) return false;
      return true;
    }
    return false;
  }

  static boolean hasTagValueAnnotation(final Method method) {
    return DomUtil.findAnnotationDFS(method, TagValue.class) != null;
  }

  public static boolean isGetter(final Method method) {
    final String name = method.getName();
    if (method.getParameterTypes().length != 0) {
      return false;
    }
    final Class<?> returnType = method.getReturnType();
    if (name.startsWith("get")) {
      return returnType != void.class;
    }
    if (name.startsWith("is")) {
      return canHaveIsPropertyGetterPrefix(method.getGenericReturnType());
    }
    return false;
  }

  public static boolean canHaveIsPropertyGetterPrefix(final Type type) {
    return boolean.class.equals(type) || Boolean.class.equals(type)
           || Boolean.class.equals(DomUtil.getGenericValueType(type));
  }

  public static boolean isTagValueSetter(final Method method) {
    boolean setter = method.getName().startsWith("set") && method.getParameterTypes().length == 1 && method.getReturnType() == void.class;
    return setter && (hasTagValueAnnotation(method) || "setValue".equals(method.getName()));
  }

  public static DomNameStrategy getDomNameStrategy(final Class<?> rawType, boolean isAttribute) {
    Class aClass = null;
    if (isAttribute) {
      NameStrategyForAttributes annotation = DomUtil.findAnnotationDFS(rawType, NameStrategyForAttributes.class);
      if (annotation != null) {
        aClass = annotation.value();
      }
    }
    if (aClass == null) {
      NameStrategy annotation = DomUtil.findAnnotationDFS(rawType, NameStrategy.class);
      if (annotation != null) {
        aClass = annotation.value();
      }
    }
    if (aClass != null) {
      if (HyphenNameStrategy.class.equals(aClass)) return DomNameStrategy.HYPHEN_STRATEGY;
      if (JavaNameStrategy.class.equals(aClass)) return DomNameStrategy.JAVA_STRATEGY;
      try {
        return (DomNameStrategy)aClass.newInstance();
      }
      catch (InstantiationException e) {
        LOG.error(e);
      }
      catch (IllegalAccessException e) {
        LOG.error(e);
      }
    }
    return null;
  }
}
