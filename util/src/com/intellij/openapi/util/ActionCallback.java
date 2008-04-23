/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.openapi.util;

import com.intellij.util.ui.update.ComparableObject;
import org.jetbrains.annotations.NotNull;

public class ActionCallback {

  ExecutionCallback myDone;
  ExecutionCallback myRejected;

  public ActionCallback() {
    myDone = new ExecutionCallback();
    myRejected = new ExecutionCallback();
  }

  public ActionCallback(int countToDone) {
    assert countToDone >= 1;

    myDone = new ExecutionCallback(countToDone);
    myRejected = new ExecutionCallback();
  }

  public void setDone() {
    myDone.setExecuted();
  }

  public void setRejected() {
    myRejected.setExecuted();
  }

  public final ActionCallback doWhenDone(@NotNull final java.lang.Runnable runnable) {
    myDone.doWhenExecuted(runnable);
    return this;
  }

  public final ActionCallback doWhenRejected(@NotNull final java.lang.Runnable runnable) {
    myRejected.doWhenExecuted(runnable);
    return this;
  }

  public final ActionCallback doWhenProcessed(@NotNull final java.lang.Runnable runnable) {
    doWhenDone(runnable);
    doWhenRejected(runnable);
    return this;
  }

  public final ActionCallback notifyWhenDone(final ActionCallback child) {
    return doWhenDone(new java.lang.Runnable() {
      public void run() {
        child.setDone();
      }
    });
  }

  public static class Done extends ActionCallback {
    public Done() {
      setDone();
    }
  }

  public static class Rejected extends ActionCallback {
    public Rejected() {
      setRejected();
    }
  }

  
  public static abstract class Runnable extends ComparableObject.Impl {

    protected Runnable() {
    }

    protected Runnable(final Object object) {
      super(object);
    }

    protected Runnable(final Object[] objects) {
      super(objects);
    }

    public abstract ActionCallback run();
  }
}
