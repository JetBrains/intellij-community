// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.scientific.figure;

import com.intellij.ui.tabs.TabInfo;
import org.jetbrains.annotations.NotNull;

public interface Figure {
  @NotNull
  TabInfo getTabInfo();

  boolean hasSearchKey();

  Object getSearchKey();
}
