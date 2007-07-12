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

import com.intellij.openapi.util.Ref;
import com.intellij.util.containers.ConcurrentHashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author max
 */
public class UniqueResultsQuery<T> implements Query<T> {
  private final Query<T> myOriginal;
  private final TObjectHashingStrategy<T> myHashingStrategy;

  public UniqueResultsQuery(final Query<T> original) {
    myOriginal = original;
    //noinspection unchecked
    myHashingStrategy = TObjectHashingStrategy.CANONICAL;
  }

  public UniqueResultsQuery(final Query<T> original, TObjectHashingStrategy<T> hashingStrategy) {
    myOriginal = original;
    myHashingStrategy = hashingStrategy;
  }

  public T findFirst() {
    return myOriginal.findFirst();
  }

  private boolean doForEach(@NotNull final Processor<T> consumer, @Nullable Ref<Set<T>> outProcessed) {
    final Set<T> processedElements = new ConcurrentHashSet<T>(myHashingStrategy);
    if (outProcessed != null) {
      outProcessed.set(processedElements);
    }
    return myOriginal.forEach(new Processor<T>() {
      public boolean process(final T t) {
        return !processedElements.add(t) || consumer.process(t);
      }
    });
  }

  public boolean forEach(@NotNull final Processor<T> consumer) {
    return doForEach(consumer, null);
  }

  @NotNull
  public Collection<T> findAll() {
    Ref<Set<T>> refProcessed = new Ref<Set<T>>();
    doForEach(new Processor<T>() {
      public boolean process(final T t) {
        return true;
      }
    }, refProcessed);

    return refProcessed.get();
  }

  public T[] toArray(final T[] a) {
    return findAll().toArray(a);
  }

  public Iterator<T> iterator() {
    return findAll().iterator();
  }
}
