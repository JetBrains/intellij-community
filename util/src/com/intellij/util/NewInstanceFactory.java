/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Factory;

import java.lang.reflect.Constructor;

public class NewInstanceFactory<T> implements Factory<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.NewInstanceFactory");
  private final Constructor myConstructor;
  private final Object[] myArgs;

  private NewInstanceFactory(Constructor constructor, Object[] args) {
    myConstructor = constructor;
    myArgs = args;
  }

  public T create() {
    try {
      return (T)myConstructor.newInstance(myArgs);
    }
    catch (Exception e) {
      LOG.error(e);
      return null;
    }
  }

  public static <T> Factory<T> fromClass(final Class<T> clazz) {
    try {
      return new NewInstanceFactory<T>(clazz.getConstructor(new Class[0]), ArrayUtil.EMPTY_OBJECT_ARRAY);
    }
    catch (NoSuchMethodException e) {
      return new Factory<T>() {
        public T create() {
          try {
            return clazz.newInstance();
          } catch (Exception e) {
            LOG.error(e);
            return null;
          }
        }
      };
    }
  }
}
