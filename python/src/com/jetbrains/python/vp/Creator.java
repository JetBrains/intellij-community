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

/**
 * Creates view and presenter allowing them to have links to each other.
 * Implement it and pass to {@link com.jetbrains.python.vp.ViewPresenterUtils#linkViewWithPresenterAndLaunch(Class, Class, Creator)}
 *
 * @author Ilya.Kazakevich
 * @param <V> view interface
 * @param <P> presenter interface
 */
public interface Creator<V, P extends Presenter> {

  /**
   * Create presenter
   *
   * @param view for that presenter
   * @return presenter
   */
  @NotNull
  P createPresenter(@NotNull V view);

  /**
   * Creates view
   *
   * @param presenter for this view
   * @return view
   */
  @NotNull
  V createView(@NotNull P presenter);

}
