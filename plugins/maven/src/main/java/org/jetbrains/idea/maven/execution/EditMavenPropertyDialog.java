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
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Map;

public class EditMavenPropertyDialog extends DialogWrapper {
  private JPanel contentPane;
  private JComboBox myNameBox;
  private JTextField myValueField;
  private final Map<String, String> myAvailableProperties;

  public EditMavenPropertyDialog(Project p, Pair<String, String> value, Map<String, String> availableProperties) {
    super(p, false);
    setTitle("Edit Maven Property");

    myAvailableProperties = availableProperties;

    installFocusListeners();
    installPropertySelectionListener();
    fillAvailableProperties();

    setValue(value);

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
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() != ItemEvent.SELECTED) return;
        String key = (String)e.getItem();
        String value = myAvailableProperties.get(key);
        if (value != null) myValueField.setText(value);
      }
    });
  }

  private void fillAvailableProperties() {
    String[] keys = myAvailableProperties.keySet().toArray(ArrayUtil.EMPTY_STRING_ARRAY);
    myNameBox.setModel(new DefaultComboBoxModel(keys));
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameBox;
  }

  private void setValue(Pair<String, String> value) {
    myNameBox.getEditor().setItem(value.getFirst());
    myValueField.setText(value.getSecond());
  }

  public Pair<String, String> getValue() {
    return new Pair<String, String>((String)myNameBox.getEditor().getItem(), myValueField.getText());
  }
}
