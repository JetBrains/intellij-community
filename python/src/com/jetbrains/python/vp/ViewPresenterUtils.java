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


import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

/**
 * Entry point to package. Use {@link #linkViewWithPresenterAndLaunch(Class, Class, Creator)}
 * @author Ilya.Kazakevich
 */
public final class ViewPresenterUtils {
  private ViewPresenterUtils() {
  }

  /**
   * TODO: Write about do not call anything in constructor
   * Creates link between view and presenter and launches them using {@link Presenter#launch()}. Be sure to read package info first.
   *
   * @param presenterInterface presenter interface
   * @param viewInterface      view interface
   * @param creator            class that handles presenter and view instances actual creation
   * @param <V>                view interface
   * @param <P>                presenter interface
   */
  public static <V, P extends Presenter> void linkViewWithPresenterAndLaunch(@NotNull Class<P> presenterInterface,
                                                                             @NotNull Class<V> viewInterface,
                                                                             @NotNull Creator<V, P> creator) {
    Preconditions.checkArgument(presenterInterface.isInterface(), "Presenter is not interface");
    Preconditions.checkArgument(viewInterface.isInterface(), "View is not interface");

    //TODO: Use cglib?
    PresenterHandler<P> presenterHandler = new PresenterHandler<>();
    ViewHandler<V> viewHandler = new ViewHandler<>();
    V viewProxy = createProxy(viewInterface, viewHandler);
    P presenterProxy = createProxy(presenterInterface, presenterHandler);

    V realView = creator.createView(presenterProxy);
    viewHandler.setRealView(realView);
    P realPresenter = creator.createPresenter(viewProxy);
    presenterHandler.setRealPresenter(realPresenter);
    realPresenter.launch();
  }


  @SuppressWarnings("unchecked") //Proxy always returns correct class
  private static <C> C createProxy(Class<C> clazz, InvocationHandler handler) {
    assert clazz != null;
    assert handler != null;
    return (C)Proxy.newProxyInstance(ViewPresenterUtils.class.getClassLoader(), new Class[]{clazz}, handler);
  }
}
