package org.intellij.plugins.xsltDebugger.ui;

import com.intellij.diagnostic.logging.AdditionalTabComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

abstract class AbstractTabComponent extends AdditionalTabComponent {
  private final String myTabTitle;

  AbstractTabComponent(String tabTitle) {
    myTabTitle = tabTitle;
  }

  @NotNull
  @Override
  public String getTabTitle() {
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
