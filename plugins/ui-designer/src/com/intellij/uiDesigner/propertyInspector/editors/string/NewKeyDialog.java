// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.editors.string;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;


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
      @Override
      public void insertUpdate(DocumentEvent e) {
        updateButton();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        updateButton();
      }

      @Override
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

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected @NotNull String getDimensionServiceKey() {
    return getClass().getName();
  }

  public String getName() {
    return myKeyNameEdit.getText();
  }

  public String getValue() {
    return myKeyValueEdit.getText();
  }
}
