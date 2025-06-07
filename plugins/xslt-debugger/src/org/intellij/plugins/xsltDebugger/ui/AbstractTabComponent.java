// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.xsltDebugger.ui;

import com.intellij.diagnostic.logging.AdditionalTabComponent;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@ApiStatus.Internal
public abstract class AbstractTabComponent extends AdditionalTabComponent {
  private final @NlsContexts.TabTitle String myTabTitle;

  AbstractTabComponent(@NlsContexts.TabTitle String tabTitle) {
    myTabTitle = tabTitle;
  }

  @Override
  public @NotNull String getTabTitle() {
    return myTabTitle;
  }

  @Override
  public JComponent getSearchComponent() {
    return null;
  }

  @Override
  public String getToolbarPlace() {
    return null;
  }

  @Override
  public JComponent getToolbarContextComponent() {
    return null;
  }

  @Override
  public boolean isContentBuiltIn() {
    return false;
  }

  @Override
  public void dispose() {
  }
}
