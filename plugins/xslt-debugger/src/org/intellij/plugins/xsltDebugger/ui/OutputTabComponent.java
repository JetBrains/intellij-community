package org.intellij.plugins.xsltDebugger.ui;

import com.intellij.diagnostic.logging.AdditionalTabComponent;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.intellij.plugins.xsltDebugger.ui.actions.OpenOutputAction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class OutputTabComponent extends AbstractTabComponent {
  private final DefaultActionGroup myOutputActions;
  private final AdditionalTabComponent myOutputConsole;

  public OutputTabComponent(AdditionalTabComponent outputConsole) {
    super("Output");

    myOutputConsole = outputConsole;
    final DefaultActionGroup outputActions = new DefaultActionGroup();
    outputActions.add(new OpenOutputAction(outputConsole));
    myOutputActions = outputActions;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
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
