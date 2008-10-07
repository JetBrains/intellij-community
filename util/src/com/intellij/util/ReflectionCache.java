/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 * @author peter
 */
@SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
public class ReflectionCache {
  private static final ConcurrentFactoryMap<Class,Class> ourSuperClasses = new ConcurrentFactoryMap<Class, Class>() {
    protected Class create(final Class key) {
      return key.getSuperclass();
    }
  };
  private static final ConcurrentFactoryMap<Class,Class[]> ourInterfaces = new ConcurrentFactoryMap<Class, Class[]>() {
    @NotNull
    protected Class[] create(final Class key) {
      return key.getInterfaces();
    }
  };
  private static final ConcurrentFactoryMap<Class, Method[]> ourMethods = new ConcurrentFactoryMap<Class, Method[]>() {
    @NotNull
    protected Method[] create(final Class key) {
      return key.getMethods();
    }
  };

  private static final ConcurrentFactoryMap<Class,Boolean> ourIsInterfaces = new ConcurrentFactoryMap<Class, Boolean>() {
    @NotNull
    protected Boolean create(final Class key) {
      return key.isInterface();
    }
  };
  private static final ConcurrentFactoryMap<Class, TypeVariable[]> ourTypeParameters = new ConcurrentFactoryMap<Class, TypeVariable[]>() {
    @NotNull
    protected TypeVariable[] create(final Class key) {
      return key.getTypeParameters();
    }
  };
  private static final ConcurrentFactoryMap<Class, Type[]> ourGenericInterfaces = new ConcurrentFactoryMap<Class, Type[]>() {
    @NotNull
    protected Type[] create(final Class key) {
      return key.getGenericInterfaces();
    }
  };
  private static final ConcurrentFactoryMap<ParameterizedType, Type[]> ourActualTypeArguments = new ConcurrentFactoryMap<ParameterizedType, Type[]>() {
    @NotNull
    protected Type[] create(final ParameterizedType key) {
      return key.getActualTypeArguments();
    }
  };

  private ReflectionCache() {
  }

  public static Class getSuperClass(Class aClass) {
    return ourSuperClasses.get(aClass);
  }

  public static Class[] getInterfaces(Class aClass) {
    return ourInterfaces.get(aClass);
  }

  public static Method[] getMethods(Class aClass) {
    return ourMethods.get(aClass);
  }

  public static boolean isAssignable(Class ancestor, Class descendant) {
    return ancestor == descendant || ancestor.isAssignableFrom(descendant);
  }

  public static boolean isInstance(Object instance, Class clazz) {
    return isAssignable(clazz, instance.getClass());
  }

  public static boolean isInterface(Class aClass) {
    return ourIsInterfaces.get(aClass);
  }

  public static <T> TypeVariable<Class<T>>[] getTypeParameters(Class<T> aClass) {
    return ourTypeParameters.get(aClass);
  }

  public static Type[] getGenericInterfaces(Class aClass) {
    return ourGenericInterfaces.get(aClass);
  }

  public static Type[] getActualTypeArguments(ParameterizedType type) {
    return ourActualTypeArguments.get(type);
  }

}
