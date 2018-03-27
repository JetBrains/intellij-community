/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.toolbox;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.function.Function;

/**
 * Iterable that splices other iterables and iterates over them sequentially.
 * User: dcheryasov
 */
public class ChainIterable<T> implements Iterable<T> {

  @NotNull
  private final LinkedList<Iterable<T>> myData = new LinkedList<>();

  public ChainIterable() {
  }

  public ChainIterable(@NotNull Iterable<T> iterable) {
    myData.add(iterable);
  }

  public ChainIterable(@NotNull T item) {
    myData.add(Collections.singleton(item));
  }

  @NotNull
  public ChainIterable<T> add(@NotNull Iterable<T> iterable) {
    myData.add(iterable);
    return this;
  }

  @NotNull
  public ChainIterable<T> addWith(@NotNull Function<Iterable<T>, Iterable<T>> mapper, @NotNull Iterable<T> iterable) {
    return add(mapper.apply(iterable));
  }

  @NotNull
  public ChainIterable<T> addItem(@NotNull T item) {
    return add(Collections.singleton(item));
  }

  public boolean isEmpty() {
    return myData.isEmpty();
  }

  @Override
  public Iterator<T> iterator() {
    return ContainerUtil.concatIterators(ContainerUtil.map(myData, Iterable::iterator));
  }

  @Override
  public String toString() {
    return StringUtil.join(this, "");
  }
}
