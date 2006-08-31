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

import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

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

  public boolean forEach(@NotNull final Processor<T> consumer) {
    final Set<T> processedElements = new THashSet<T>(myHashingStrategy);
    return myOriginal.forEach(new Processor<T>() {
      public boolean process(final T t) {
        if (processedElements.contains(t)) return true;
        processedElements.add(t);

        if (!consumer.process(t)) return false;

        return true;
      }
    });
  }

  @NotNull
  public Collection<T> findAll() {
    final List<T> result = new ArrayList<T>();
    forEach(new Processor<T>() {
      public boolean process(final T t) {
        result.add(t);
        return true;
      }
    });

    return result;
  }

  public T[] toArray(final T[] a) {
    return findAll().toArray(a);
  }

  public Iterator<T> iterator() {
    return findAll().iterator();
  }
}
