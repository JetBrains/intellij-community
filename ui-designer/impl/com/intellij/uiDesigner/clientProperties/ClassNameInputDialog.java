/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.clientProperties;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.EditorTextField;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.Component;

/**
 * @author yole
 */
public class ClassNameInputDialog extends DialogWrapper {
  private EditorTextField myEditorTextField1;
  private JPanel myRootPanel;

  public ClassNameInputDialog(Component parent) {
    super(parent, false);
    init();
    setTitle(UIDesignerBundle.message("client.properties.title"));
  }

  private void createUIComponents() {
    myEditorTextField1 = new EditorTextField("");
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myEditorTextField1;
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  public String getClassName() {
    return myEditorTextField1.getDocument().getText();
  }
}
