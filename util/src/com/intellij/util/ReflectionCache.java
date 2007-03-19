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

import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ConcurrentWeakFactoryMap;
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
  private static final ConcurrentWeakFactoryMap<Class,Class[]> ourInterfaces = new ConcurrentWeakFactoryMap<Class, Class[]>() {
    @NotNull
    protected Class[] create(final Class key) {
      return key.getInterfaces();
    }
  };
  private static final ConcurrentWeakFactoryMap<Class, Method[]> ourMethods = new ConcurrentWeakFactoryMap<Class, Method[]>() {
    @NotNull
    protected Method[] create(final Class key) {
      return key.getMethods();
    }
  };

  private static final ConcurrentWeakFactoryMap<Class, ConcurrentFactoryMap<Class,Boolean>> ourAssignables = new ConcurrentWeakFactoryMap<Class, ConcurrentFactoryMap<Class, Boolean>>() {
    @NotNull
    protected ConcurrentFactoryMap<Class, Boolean> create(final Class key1) {
      return new ConcurrentFactoryMap<Class, Boolean>() {
        @NotNull
        protected Boolean create(final Class key2) {
          return key1.isAssignableFrom(key2);
        }
      };
    }
  };

  private static final ConcurrentWeakFactoryMap<Class,Boolean> ourIsInterfaces = new ConcurrentWeakFactoryMap<Class, Boolean>() {
    @NotNull
    protected Boolean create(final Class key) {
      return key.isInterface();
    }
  };
  private static final ConcurrentWeakFactoryMap<Class, TypeVariable[]> ourTypeParameters = new ConcurrentWeakFactoryMap<Class, TypeVariable[]>() {
    @NotNull
    protected TypeVariable[] create(final Class key) {
      return key.getTypeParameters();
    }
  };
  private static final ConcurrentWeakFactoryMap<Class, Type[]> ourGenericInterfaces = new ConcurrentWeakFactoryMap<Class, Type[]>() {
    @NotNull
    protected Type[] create(final Class key) {
      return key.getGenericInterfaces();
    }
  };
  private static final ConcurrentWeakFactoryMap<ParameterizedType, Type[]> ourActualTypeArguments = new ConcurrentWeakFactoryMap<ParameterizedType, Type[]>() {
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
    return ancestor == descendant || ourAssignables.get(ancestor).get(descendant);
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
