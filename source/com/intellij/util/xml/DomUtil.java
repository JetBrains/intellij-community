/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.List;
import java.util.Collection;

/**
 * @author peter
 */
public class DomUtil {
  public static Class extractParameterClassFromGenericType(Type type) {
    if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType)type;
      if (parameterizedType.getRawType() == GenericValue.class) {
        final Type[] arguments = parameterizedType.getActualTypeArguments();
        if (arguments.length == 1 && arguments[0] instanceof Class) {
          return (Class)arguments[0];
        }
      }
    }
    return null;
  }

  public static boolean isGenericValueType(Type type) {
    return extractParameterClassFromGenericType(type) != null;
  }

  public static Class<?> getClassFromGenericType(final Type genericType, final Type classType) {
    if (genericType instanceof TypeVariable) {
      TypeVariable typeVariable = (TypeVariable)genericType;
      if (classType instanceof ParameterizedType) {
        ParameterizedType parameterizedType = (ParameterizedType)classType;
        if (parameterizedType.getRawType() == GenericValue.class) {
          final Type[] arguments = parameterizedType.getActualTypeArguments();
          final Type rawType = parameterizedType.getRawType();
          if (rawType instanceof Class) {
            Class aClass = (Class)rawType;
            final TypeVariable[] typeParameters = aClass.getTypeParameters();
            if (typeParameters.length ==1 && typeParameters[0].equals(typeVariable) && arguments[0] instanceof Class) {
              return (Class)arguments[0];
            }
          }
        }
      }
    }
    return null;
  }

  public static Class getRawType(Type type) {
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
}
