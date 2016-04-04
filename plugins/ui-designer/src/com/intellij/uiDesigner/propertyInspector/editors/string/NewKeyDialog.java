/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.propertyInspector.editors.string;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.NotNull;

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

  @NotNull
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  public String getName() {
    return myKeyNameEdit.getText();
  }

  public String getValue() {
    return myKeyValueEdit.getText();
  }
}
