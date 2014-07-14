/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.vp;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * Wrapper for presenter.
 * @author Ilya.Kazakevich
 * @param <C> presenter class
 */
class PresenterHandler<C> implements InvocationHandler {
  /**
   * Presenter, created by user with {@link com.jetbrains.python.vp.Creator#createPresenter(Object)}
   */
  private C realPresenter;

  void setRealPresenter(@NotNull C realPresenter) {
    this.realPresenter = realPresenter;
  }

  @Override
  public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
    /**
     * TODO: Implement async call.
     * The idea is void methods marked with @Async should be called in background thread.
     * That will allow presenter to be agnostic about EDT
     */
    return method.invoke(realPresenter, args);
  }
}
