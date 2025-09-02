// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.xsltDebugger.ui;

import com.intellij.diagnostic.logging.AdditionalTabComponent;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.intellij.plugins.xsltDebugger.XsltDebuggerBundle;
import org.intellij.plugins.xsltDebugger.ui.actions.OpenOutputAction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@ApiStatus.Internal
public final class OutputTabComponent extends AbstractTabComponent {
  private final DefaultActionGroup myOutputActions;
  private final AdditionalTabComponent myOutputConsole;

  public OutputTabComponent(AdditionalTabComponent outputConsole) {
    super(XsltDebuggerBundle.message("tab.title.output"));

    myOutputConsole = outputConsole;
    final DefaultActionGroup outputActions = new DefaultActionGroup();
    outputActions.add(new OpenOutputAction(outputConsole));
    myOutputActions = outputActions;
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myOutputConsole;
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return myOutputConsole.getPreferredFocusableComponent();
  }

  @Override
  public ActionGroup getToolbarActions() {
    return myOutputActions;
  }
}
