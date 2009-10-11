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

public abstract class ThreadLocalDefendedInvoker<T> {
  protected final ThreadLocal<Boolean> myThreadLocal;

  protected ThreadLocalDefendedInvoker() {
    myThreadLocal = new ThreadLocal<Boolean>() {
      @Override
      protected Boolean initialValue() {
        return Boolean.TRUE;
      }
    };
  }

  protected abstract T execute();

  public boolean isInside() {
    return ! Boolean.TRUE.equals(myThreadLocal.get());
  }

  public boolean isOutside() {
    return ! isInside();
  }

  public T executeDefended() {
    try {
      myThreadLocal.set(Boolean.FALSE);
      return execute();
    } finally {
      myThreadLocal.set(Boolean.TRUE);
    }
  }
}
