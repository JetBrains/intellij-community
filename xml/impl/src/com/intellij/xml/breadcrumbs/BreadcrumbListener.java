// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.breadcrumbs;

import com.intellij.ui.components.breadcrumbs.Crumb;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;


public interface BreadcrumbListener {
  /**
   * Listener for breadcrumbs updates
   *
   * @param crumbs             updated collection of breadcrumbs
   * @param navigationConsumer consumer, that should be called in order to properly navigate by breadcrumb
   */
  void breadcrumbsChanged(@Nullable Iterable<? extends Crumb> crumbs, BiConsumer<NavigatableCrumb, Boolean> navigationConsumer);
}
