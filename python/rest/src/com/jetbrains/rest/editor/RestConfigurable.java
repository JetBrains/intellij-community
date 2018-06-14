// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest.editor;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.LabeledComponent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class RestConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  public static final String ID = "com.jetbrains.rest.editor.RestConfigurable";
  private final JPanel myPanel = new JPanel(new BorderLayout());
  private final ComboBox<String> myComboBox;
  public static final String JAVA_FX = "JavaFX";
  public static final String SWING = "Swing";

  RestConfigurable() {
    myComboBox = new ComboBox<>();
    myComboBox.addItem(JAVA_FX);
    myComboBox.addItem(SWING);
    myComboBox.setSelectedItem(RestSettings.getInstance().getCurrentPanel());
    LabeledComponent<JComponent> component = new LabeledComponent<>();
    component.setComponent(myComboBox);
    component.setText("Preview panel:");
    component.setLabelLocation(BorderLayout.WEST);
    myPanel.add(component, BorderLayout.NORTH);
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "ReStructured text";
  }

  @Override
  public String getHelpTopic() {
    return ID;
  }

  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public void reset() {
  }

  @Override
  public boolean isModified() {
    return !RestSettings.getInstance().getCurrentPanel().equals(myComboBox.getSelectedItem());
  }

  @Override
  public void apply() {
    final String selectedItem = (String)myComboBox.getSelectedItem();
    if (selectedItem != null) {
      RestSettings.getInstance().setCurrentPanel(selectedItem);
    }
  }

}
