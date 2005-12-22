package com.intellij.uiDesigner.propertyInspector.editors.string;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.uiDesigner.UIDesignerBundle;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class NewKeyDialog extends DialogWrapper {
  private JTextField myKeyNameEdit;
  private JTextField myKeyValueEdit;
  private JPanel myPanel;

  public NewKeyDialog(Component parent) {
    super(parent, true);
    setTitle(UIDesignerBundle.message("key.chooser.new.property.title"));
    init();
  }

  @Override public JComponent getPreferredFocusedComponent() {
    return myKeyNameEdit;
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public String getName() {
    return myKeyNameEdit.getText();
  }

  public String getValue() {
    return myKeyValueEdit.getText();
  }
}
