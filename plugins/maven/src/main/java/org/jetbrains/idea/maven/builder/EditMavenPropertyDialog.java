package org.jetbrains.idea.maven.builder;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.*;
import java.util.Map;

public class EditMavenPropertyDialog extends DialogWrapper {
  private JPanel contentPane;
  private JComboBox myNameBox;
  private JTextField myValueField;
  private Map<String, String> myAvailableProperties;

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
    String[] keys = myAvailableProperties.keySet().toArray(new String[0]);
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
