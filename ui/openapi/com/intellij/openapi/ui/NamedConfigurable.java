/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.ui;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;

/**
 * User: anna
 * Date: 26-May-2006
 */
public abstract class NamedConfigurable<T> implements Configurable {
  private JTextField myNameField;
  private JPanel myNamePanel;
  private JPanel myWholePanel;
  private JPanel myOptionsPanel;
  private JComponent myOptionsComponent;
  private boolean myNameEditable;

  protected NamedConfigurable() {
    this(false, null);
  }

  protected NamedConfigurable(boolean isNameEditable, @Nullable final Runnable updateTree) {
    myNameEditable = isNameEditable;
    myNamePanel.setVisible(myNameEditable);
    if (myNameEditable){
      myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
        protected void textChanged(DocumentEvent e) {
          setDisplayName(myNameField.getText());
          if (updateTree != null){
            updateTree.run();
          }
        }
      });
    }
  }

  public boolean isNameEditable() {
    return myNameEditable;
  }

  public abstract void setDisplayName(String name);
  public abstract T getEditableObject();
  public abstract String getBannerSlogan();

  public final JComponent createComponent() {
    if (myOptionsComponent == null){
      myOptionsComponent = createOptionsPanel();
    }
    myOptionsPanel.add(myOptionsComponent, BorderLayout.CENTER);
    myNameField.setText(getDisplayName());
    return myWholePanel;
  }

  public abstract JComponent createOptionsPanel();


}
