package com.intellij.ide.hierarchy.method;

import com.intellij.openapi.actionSystem.Presentation;

public final class OverrideMethodAction extends OverrideImplementMethodAction {
  protected final void update(final Presentation presentation, final int toImplement, final int toOverride) {
    if (toOverride > 0) {
      presentation.setEnabled(true);
      presentation.setVisible(true);
      presentation.setText(toOverride == 1 ? "Override Method" : "Override Methods");
    }
    else {
      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
  }

}
