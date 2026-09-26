// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

/**
 * @see DomManager#createModelMerger() 
 */
public interface ModelMerger {
  <T> T mergeModels(Class<T> aClass, T... implementations);

  <T> T mergeModels(Class<T> aClass, Collection<? extends T> implementations);

  <T> void addInvocationStrategy(Class<T> aClass, InvocationStrategy<T> strategy);

  <T> void addMergingStrategy(Class<T> aClass, MergingStrategy<T> strategy);


  abstract class InvocationStrategy<T> {
    public abstract boolean accepts(Method method);
    public abstract Object invokeMethod(JavaMethod method, final T proxy, final Object[] args, List<? extends T> implementations) throws IllegalAccessException,
                                                                                                                                         InvocationTargetException;
  }

  abstract class MergingStrategy<T> {
    public abstract @Nullable T mergeChildren(Class<T> type, List<? extends T> implementations);
  }
}
