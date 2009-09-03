package com.intellij.uiDesigner.propertyInspector.editors.string;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.uiDesigner.UIDesignerBundle;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
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
    updateButton();
    myKeyNameEdit.getDocument().addDocumentListener(new DocumentListener() {
      public void insertUpdate(DocumentEvent e) {
        updateButton();
      }

      public void removeUpdate(DocumentEvent e) {
        updateButton();
      }

      public void changedUpdate(DocumentEvent e) {
        updateButton();
      }
    });
  }

  private void updateButton() {
    setOKActionEnabled(myKeyNameEdit.getText().length() > 0);
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
