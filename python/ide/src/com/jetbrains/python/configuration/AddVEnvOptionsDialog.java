package com.jetbrains.python.configuration;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBCheckBox;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class AddVEnvOptionsDialog extends DialogWrapper {
  private JBCheckBox myUseForThisProjectJBCheckBox;
  private JBCheckBox myMakeAvailableToAllJBCheckBox;
  private JPanel myMainPanel;

  public AddVEnvOptionsDialog(Component parent) {
    super(parent, false);
    init();
    setTitle("Add Virtualenv");
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  public boolean useForThisProject() {
    return myUseForThisProjectJBCheckBox.isSelected();
  }

  public boolean makeAvailableToAll() {
    return myMakeAvailableToAllJBCheckBox.isSelected();
  }
}
