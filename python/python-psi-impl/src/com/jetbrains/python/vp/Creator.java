// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.vp;


import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Creates view and presenter allowing them to have links to each other.
 * Implement it and pass to {@link ViewPresenterUtils#linkViewWithPresenterAndLaunch(Class, Class, Creator)}
 *
 * @param <V> view interface
 * @param <P> presenter interface
 * @author Ilya.Kazakevich
 */
@ApiStatus.Internal
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
