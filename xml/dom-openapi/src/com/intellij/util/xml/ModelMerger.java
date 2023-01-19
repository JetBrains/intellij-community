/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
    @Nullable
    public abstract T mergeChildren(Class<T> type, List<? extends T> implementations);
  }
}
