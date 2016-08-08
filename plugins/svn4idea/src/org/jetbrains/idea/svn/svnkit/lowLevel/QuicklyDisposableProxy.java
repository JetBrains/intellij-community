/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.svnkit.lowLevel;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProcessCanceledException;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/15/12
 * Time: 3:03 PM
 */
public abstract class QuicklyDisposableProxy<T> implements Disposable {
  private final AtomicReference<T> myRef;

  protected QuicklyDisposableProxy(final T t) {
    myRef = new AtomicReference<>(t);
  }

  @Override
  public void dispose() {
    myRef.set(null);
  }

  protected T getRef() {
    T t = myRef.get();
    if (t == null) throw new ProcessCanceledException();
    return t;
  }
}
