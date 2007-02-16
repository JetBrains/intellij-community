/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.SoftFactoryMap;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * @author peter
 */
public class ReflectionCache {
  private static final Map<Class,Class> ourSuperClasses = new WeakHashMap<Class, Class>();
  private static final SoftFactoryMap<Class,Class[]> ourInterfaces = new SoftFactoryMap<Class, Class[]>() {
    @NotNull
    protected Class[] create(final Class key) {
      return key.getInterfaces();
    }
  };
  private static final SoftFactoryMap<Class, Method[]> ourMethods = new SoftFactoryMap<Class, Method[]>() {
    @NotNull
    protected Method[] create(final Class key) {
      return key.getMethods();
    }
  };
  private static final SoftFactoryMap<Pair<Class,Class>,Boolean> ourAssignables = new SoftFactoryMap<Pair<Class, Class>, Boolean>() {
    protected Boolean create(final Pair<Class, Class> key) {
      return key.getFirst().isAssignableFrom(key.getSecond());
    }
  };

  private static final SoftFactoryMap<Class,Boolean> ourIsInterfaces = new SoftFactoryMap<Class, Boolean>() {
    @NotNull
    protected Boolean create(final Class key) {
      return key.isInterface();
    }
  };
  private static final SoftFactoryMap<Class, TypeVariable[]> ourTypeParameters = new SoftFactoryMap<Class, TypeVariable[]>() {
    @NotNull
    protected TypeVariable[] create(final Class key) {
      return key.getTypeParameters();
    }
  };
  private static final SoftFactoryMap<Class, Type[]> ourGenericInterfaces = new SoftFactoryMap<Class, Type[]>() {
    @NotNull
    protected Type[] create(final Class key) {
      return key.getGenericInterfaces();
    }
  };
  private static final SoftFactoryMap<ParameterizedType, Type[]> ourActualTypeArguments = new SoftFactoryMap<ParameterizedType, Type[]>() {
    @NotNull
    protected Type[] create(final ParameterizedType key) {
      return key.getActualTypeArguments();
    }
  };

  private ReflectionCache() {
  }

  public static Class getSuperClass(Class aClass) {
    Class superClass = ourSuperClasses.get(aClass);
    if (superClass == null) {
      ourSuperClasses.put(aClass, superClass = aClass.getSuperclass());
    }
    return superClass;
  }

  public static Class[] getInterfaces(Class aClass) {
    return ourInterfaces.get(aClass);
  }

  public static Method[] getMethods(Class aClass) {
    return ourMethods.get(aClass);
  }

  public static boolean isAssignable(Class ancestor, Class descendant) {
    return ancestor == descendant || ourAssignables.get(Pair.create(ancestor, descendant));
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
