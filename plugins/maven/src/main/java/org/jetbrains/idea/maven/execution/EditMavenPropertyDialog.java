// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomBundle;

import javax.swing.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Arrays;
import java.util.Map;

public class EditMavenPropertyDialog extends DialogWrapper {
  private JPanel contentPane;
  private JComboBox myNameBox;
  private JTextField myValueField;
  private final Map<String, String> myAvailableProperties;

  public EditMavenPropertyDialog(@Nullable Pair<String, String> value, Map<String, String> availableProperties) {
    super(false);
    setTitle(MavenDomBundle.message(value == null ? "property.title.add" : "property.title.edit"));

    myAvailableProperties = availableProperties;

    installFocusListeners();
    fillAvailableProperties();

    if (value != null) {
      //noinspection HardCodedStringLiteral
      myNameBox.getEditor().setItem(value.getFirst());
      myValueField.setText(value.getSecond());
    }

    installPropertySelectionListener();

    init();
  }

  private void installFocusListeners() {
    myNameBox.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        myNameBox.getEditor().selectAll();
      }
    });
    myValueField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        myValueField.selectAll();
      }
    });
  }

  private void installPropertySelectionListener() {
    myNameBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() != ItemEvent.SELECTED) return;
        String key = (String)e.getItem();
        String value = myAvailableProperties.get(key);
        if (value != null) myValueField.setText(value);
      }
    });
  }

  private void fillAvailableProperties() {
    String[] keys = ArrayUtilRt.toStringArray(myAvailableProperties.keySet());
    Arrays.sort(keys);
    myNameBox.setModel(new DefaultComboBoxModel(keys));
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameBox;
  }

  public Pair<String, String> getValue() {
    return Pair.create((String)myNameBox.getEditor().getItem(), myValueField.getText());
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#org.jetbrains.idea.maven.execution.EditMavenPropertyDialog";
  }
}
