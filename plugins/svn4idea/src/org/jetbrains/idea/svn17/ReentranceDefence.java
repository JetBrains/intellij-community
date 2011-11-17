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
package org.jetbrains.idea.svn17;

public class ReentranceDefence {
  protected final ThreadLocal<Boolean> myThreadLocal;

  protected ReentranceDefence() {
    myThreadLocal = new ThreadLocal<Boolean>() {
      @Override
      protected Boolean initialValue() {
        return Boolean.TRUE;
      }
    };
  }

  public static <T> T executeReentrant(final ReentranceDefence defence, final MyControlled<T> controlled) {
    if (defence.isInside()) {
      return controlled.executeMeSimple();
    }
    return controlled.executeMe();
  }

  public boolean isInside() {
    return ! Boolean.TRUE.equals(myThreadLocal.get());
  }

  public boolean isOutside() {
    return ! isInside();
  }

  public void executeOtherDefended(final Runnable runnable) {
    try {
      myThreadLocal.set(Boolean.FALSE);
      runnable.run();
    } finally {
      myThreadLocal.set(Boolean.TRUE);
    }
  }

  public interface MyControlled<T> {
    T executeMe();
    T executeMeSimple();
  }
}
