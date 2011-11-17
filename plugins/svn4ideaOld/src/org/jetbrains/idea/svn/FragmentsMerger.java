/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.Factory;
import com.intellij.util.Consumer;

public class FragmentsMerger<U, T extends Consumer<U>> {
  private T myData;
  private final Object myLock;
  private final Factory<T> myFactory;

  public FragmentsMerger(final Factory<T> factory) {
    myFactory = factory;
    myLock = new Object();
    myData = myFactory.create();
  }

  public void add(final U data) {
    synchronized (myLock) {
      // only T decides what to do with input, not the external code
      myData.consume(data);
    }
  }

  public T receive() {
    synchronized (myLock) {
      final T copy = myData;
      myData = myFactory.create();
      return copy;
    }
  }
}
