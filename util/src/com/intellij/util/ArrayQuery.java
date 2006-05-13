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

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author max
 */
public class ArrayQuery<T> implements Query<T> {
  private T[] myArray;

  public ArrayQuery(final T[] array) {
    myArray = array;
  }

  @NotNull
  public Collection<T> findAll() {
    return Arrays.asList(myArray);
  }

  public T findFirst() {
    return myArray.length > 0 ? myArray[0] : null;
  }

  public boolean forEach(final Processor<T> consumer) {
    for (T t : myArray) {
      if (!consumer.process(t)) return false;
    }
    return true;
  }

  public T[] toArray(final T[] a) {
    return myArray;
  }

  public Iterator<T> iterator() {
    return Arrays.asList(myArray).iterator();
  }
}
