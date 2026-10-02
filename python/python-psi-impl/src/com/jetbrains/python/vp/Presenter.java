// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.vp;

import org.jetbrains.annotations.ApiStatus;


/**
 * Interface each presenter should implement
 * @author Ilya.Kazakevich
 */
@ApiStatus.Internal
public interface Presenter {
  /**
   * Launches dialog. Presenter should fetch data and start view.
   * TODO: Say you run initand show and launch
   */
  void launch();
}
