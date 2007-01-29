package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.Arrays;

public class ReflectionUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ReflectionUtil");

  private ReflectionUtil() {
  }

  @Nullable
  public static Method getMethod(Class aClass, @NonNls String name, Class... paramTypes) {
    try {
      return aClass.getMethod(name, paramTypes);
    }
    catch (NoSuchMethodException e) {
      LOG.error(e);
      return null;
    }
  }

  @Nullable
  public static Type resolveVariable(TypeVariable variable, final Class classType) {
    final Class aClass = getRawType(classType);
    int index = ContainerUtil.findByEquals(ReflectionCache.getTypeParameters(aClass), variable);
    if (index >= 0) {
      return variable;
    }

    final Class[] classes = ReflectionCache.getInterfaces(aClass);
    final Type[] genericInterfaces = ReflectionCache.getGenericInterfaces(aClass);
    for (int i = 0; i < classes.length; i++) {
      Class anInterface = classes[i];
      final Type resolved = resolveVariable(variable, anInterface);
      if (resolved instanceof Class || resolved instanceof ParameterizedType) {
        return resolved;
      }
      if (resolved instanceof TypeVariable) {
        final TypeVariable typeVariable = (TypeVariable)resolved;
        index = ContainerUtil.findByEquals(ReflectionCache.getTypeParameters(anInterface), typeVariable);
        if (index < 0) {
          LOG.assertTrue(false, "Cannot resolve type variable:\n" +
                              "typeVariable = " + typeVariable + "\n" +
                              "genericDeclaration = " + declarationToString(typeVariable.getGenericDeclaration()) + "\n" +
                              "searching in " + declarationToString(anInterface));
        }
        final Type type = genericInterfaces[i];
        if (type instanceof Class) {
          return Object.class;
        }
        if (type instanceof ParameterizedType) {
          return getActualTypeArguments(((ParameterizedType)type))[index];
        }
        throw new AssertionError("Invalid type: " + type);
      }
    }
    return null;
  }

  public static String declarationToString(final GenericDeclaration anInterface) {
    return anInterface.toString() + Arrays.asList(anInterface.getTypeParameters()) + " loaded by " + ((Class)anInterface).getClassLoader();
  }

  public static Class<?> getRawType(Type type) {
    if (type instanceof Class) {
      return (Class)type;
    }
    if (type instanceof ParameterizedType) {
      return getRawType(((ParameterizedType)type).getRawType());
    }
    if (type instanceof GenericArrayType) {
      //todo[peter] don't create new instance each time
      return Array.newInstance(getRawType(((GenericArrayType)type).getGenericComponentType()), 0).getClass();
    }
    assert false : type;
    return null;
  }

  public static Type[] getActualTypeArguments(final ParameterizedType parameterizedType) {
    return ReflectionCache.getActualTypeArguments(parameterizedType);
  }

  @Nullable
  public static Class<?> substituteGenericType(final Type genericType, final Type classType) {
    if (genericType instanceof TypeVariable) {
      final Class<?> aClass = getRawType(classType);
      final Type type = resolveVariable((TypeVariable)genericType, aClass);
      if (type instanceof Class) {
        return (Class)type;
      }
      if (type instanceof ParameterizedType) {
        return (Class<?>)((ParameterizedType)type).getRawType();
      }
      if (type instanceof TypeVariable && classType instanceof ParameterizedType) {
        final int index = ContainerUtil.findByEquals(ReflectionCache.getTypeParameters(aClass), type);
        if (index >= 0) {
          return getRawType(getActualTypeArguments(((ParameterizedType)classType))[index]);
        }
      }
    } else {
      return getRawType(genericType);
    }
    return null;
  }
}
