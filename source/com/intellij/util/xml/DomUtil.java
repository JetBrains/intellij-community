/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public class DomUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.DomUtil");

  public static Class extractParameterClassFromGenericType(Type type) {
    if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType)type;
      final Type rawType = parameterizedType.getRawType();
      if (isGenericValue(rawType)) {
        final Type[] arguments = parameterizedType.getActualTypeArguments();
        if (arguments.length == 1 && arguments[0] instanceof Class) {
          return (Class)arguments[0];
        }
      }
    }
    return null;
  }

  private static boolean isGenericValue(final Type rawType) {
    return rawType == GenericDomValue.class || rawType == GenericAttributeValue.class;
  }

  public static boolean isGenericValueType(Type type) {
    return extractParameterClassFromGenericType(type) != null;
  }

  public static Class<?> getClassFromGenericType(final Type genericType, final Type classType) {
    if (genericType instanceof TypeVariable && classType instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType)classType;
      final Type rawType = parameterizedType.getRawType();
      if (isGenericValue(rawType)) {
        final Type[] arguments = parameterizedType.getActualTypeArguments();
        final TypeVariable[] typeParameters = ((Class)rawType).getTypeParameters();
        if (typeParameters.length == 1 && arguments[0] instanceof Class) {
          return (Class)arguments[0];
        }
      }
    }
    return null;
  }

  public static Class<?> getRawType(Type type) {
    if (type instanceof Class) {
      return (Class)type;
    }
    if (type instanceof ParameterizedType) {
      return getRawType(((ParameterizedType)type).getRawType());
    }
    assert false : type;
    return null;
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
              if (upperBounds.length == 1 && isDomElement(upperBounds[0])) {
                return upperBounds[0];
              }
            }
            else if (argument instanceof ParameterizedType) {
              if (extractParameterClassFromGenericType(argument) != null) {
                return argument;
              }
            }
            else if (isDomElement(argument)) {
              return argument;
            }
          }
        }
      }
    }
    return null;
  }

  public static boolean isDomElement(final Type type) {
    return type instanceof Class && DomElement.class.isAssignableFrom((Class)type);
  }

  public static DomNameStrategy getDomNameStrategy(final Class<?> rawType) {
    final NameStrategy annotation = rawType.getAnnotation(NameStrategy.class);
    if (annotation != null) {
      final Class aClass = annotation.value();
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
    for (Class aClass : rawType.getInterfaces()) {
      final DomNameStrategy strategy = getDomNameStrategy(aClass);
      if (strategy != null) {
        return strategy;
      }
    }
    return null;
  }

  public static boolean isTagValueGetter(final Method method) {
    return isGetter(method) && (method.getAnnotation(TagValue.class) != null || "getValue".equals(method.getName()));
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
           || Boolean.class.equals(extractParameterClassFromGenericType(type));
  }
}
