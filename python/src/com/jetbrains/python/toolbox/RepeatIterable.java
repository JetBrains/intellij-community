/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Iterable that endlessly repeat the same sequence.
 * User: dcheryasov
 * Date: Nov 8, 2009 6:32:08 AM
 */
public class RepeatIterable<T> implements Iterable<T> {
  private final List<T> master;

  /**
   * Create an iterator that repeats the contents of given list.
   * @param master the list to repeat
   */
  public RepeatIterable(@NotNull List<T> master) {
    this.master = master;
  }

  /**
   * Create an iterator that repeats given value.
   * @param single the value to repeat
   */
  public RepeatIterable(@NotNull T single) {
    master = new ArrayList<T>(1);
    master.add(single);
  }

  public Iterator<T> iterator() {
    return new RepeatIterator<T>(master);
  }
}
