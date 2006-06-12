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

import com.intellij.util.containers.WeakFactoryMap;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * @author peter
 */
public class ReflectionCache {
  private static final Map<Class,Class> ourSuperClasses = new WeakHashMap<Class, Class>();
  private static final WeakFactoryMap<Class,Class[]> ourInterfaces = new WeakFactoryMap<Class, Class[]>() {
    @NotNull
    protected Class[] create(final Class key) {
      return key.getInterfaces();
    }
  };
  private static final WeakFactoryMap<Class, Method[]> ourMethods = new WeakFactoryMap<Class, Method[]>() {
    @NotNull
    protected Method[] create(final Class key) {
      return key.getMethods();
    }
  };
  private static final WeakFactoryMap<Class,WeakFactoryMap<Class,Boolean>> ourAssignables = new WeakFactoryMap<Class, WeakFactoryMap<Class, Boolean>>() {
    @NotNull
    protected WeakFactoryMap<Class, Boolean> create(final Class key1) {
      return new WeakFactoryMap<Class, Boolean>() {
        @NotNull
        protected Boolean create(final Class key2) {
          return key1.isAssignableFrom(key2);
        }
      };
    }
  };
  private static final WeakFactoryMap<Class,Boolean> ourIsInterfaces = new WeakFactoryMap<Class, Boolean>() {
    @NotNull
    protected Boolean create(final Class key) {
      return key.isInterface();
    }
  };
  private static final WeakFactoryMap<Class, TypeVariable[]> ourTypeParameters = new WeakFactoryMap<Class, TypeVariable[]>() {
    @NotNull
    protected TypeVariable[] create(final Class key) {
      return key.getTypeParameters();
    }
  };
  private static final WeakFactoryMap<Class, Type[]> ourGenericInterfaces = new WeakFactoryMap<Class, Type[]>() {
    @NotNull
    protected Type[] create(final Class key) {
      return key.getGenericInterfaces();
    }
  };

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
    return ourAssignables.get(ancestor).get(descendant);
  }

  public static boolean isInterface(Class aClass) {
    return ourIsInterfaces.get(aClass);
  }

  public static TypeVariable[] getTypeParameters(Class aClass) {
    return ourTypeParameters.get(aClass);
  }

  public static Type[] getGenericInterfaces(Class aClass) {
    return ourGenericInterfaces.get(aClass);
  }

}
