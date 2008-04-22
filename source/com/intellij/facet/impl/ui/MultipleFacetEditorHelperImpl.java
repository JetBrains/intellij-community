package com.intellij.facet.impl.ui;

import com.intellij.facet.ui.FacetEditor;
import com.intellij.facet.ui.MultipleFacetEditorHelper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.NotNullFunction;
import com.intellij.util.ui.ThreeStateCheckBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class MultipleFacetEditorHelperImpl implements MultipleFacetEditorHelper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.ui.MultipleFacetSettingsEditor");
  private List<AbstactBinding> myBindings = new ArrayList<AbstactBinding>();

  public void bind(@NotNull ThreeStateCheckBox common, @NotNull FacetEditor[] editors, @NotNull NotNullFunction<FacetEditor, JCheckBox> fun) {
    List<JCheckBox> checkBoxesList = new ArrayList<JCheckBox>();
    for (FacetEditor editor : editors) {
      checkBoxesList.add(fun.fun(editor));
    }

    CheckBoxBinding checkBoxBinding = new CheckBoxBinding(common, checkBoxesList);
    myBindings.add(checkBoxBinding);
  }

  public void bind(@NotNull JTextField common, @NotNull FacetEditor[] editors, @NotNull NotNullFunction<FacetEditor, JTextField> fun) {
    List<JTextField> componentsList = new ArrayList<JTextField>();
    for (FacetEditor editor : editors) {
      componentsList.add(fun.fun(editor));
    }

    TextFieldBinding binding = new TextFieldBinding(common, componentsList);
    myBindings.add(binding);
  }

  public void bind(@NotNull JComboBox common, @NotNull FacetEditor[] editors, @NotNull NotNullFunction<FacetEditor, JComboBox> fun) {
    List<JComboBox> componentsList = new ArrayList<JComboBox>();
    for (FacetEditor editor : editors) {
      componentsList.add(fun.fun(editor));
    }

    CombobBoxBinding binding = new CombobBoxBinding(common, componentsList);
    myBindings.add(binding);
  }

  public void unbind() {
    for (AbstactBinding binding : myBindings) {
      binding.unbind();
    }
    myBindings.clear();
  }

  private static abstract class AbstactBinding {
    public abstract void unbind();
  }

  private static class CheckBoxBinding extends AbstactBinding implements ActionListener {
    private final ThreeStateCheckBox myCommon;
    private final List<JCheckBox> myCheckBoxesList;
    private final List<Boolean> myInitialValues;

    public CheckBoxBinding(final ThreeStateCheckBox common, final List<JCheckBox> checkBoxesList) {
      LOG.assertTrue(!checkBoxesList.isEmpty());
      myCommon = common;
      myCheckBoxesList = checkBoxesList;

      Boolean initialValue = checkBoxesList.get(0).isSelected();
      myInitialValues = new ArrayList<Boolean>();
      for (JCheckBox checkBox : checkBoxesList) {
        boolean value = checkBox.isSelected();
        myInitialValues.add(value);
        if (initialValue != null && value != initialValue) {
          initialValue = null;
        }
      }
      if (initialValue != null) {
        common.setThirdStateEnabled(false);
        common.setSelected(initialValue);
      }
      else {
        common.setThirdStateEnabled(true);
        common.setState(ThreeStateCheckBox.State.DONT_CARE);
      }

      myCommon.addActionListener(this);
    }

    public void actionPerformed(final ActionEvent e) {
      ThreeStateCheckBox.State state = myCommon.getState();
      for (int i = 0; i < myCheckBoxesList.size(); i++) {
        boolean value = state == ThreeStateCheckBox.State.SELECTED ? true : state == ThreeStateCheckBox.State.NOT_SELECTED ? false : myInitialValues.get(i);
        JCheckBox checkBox = myCheckBoxesList.get(i);

        if (value != checkBox.isSelected()) {
          ButtonModel model = checkBox.getModel();
          model.setArmed(true);
          model.setPressed(true);
          model.setPressed(false);
          model.setArmed(false);
        }
      }
    }

    public void unbind() {
      myCommon.removeActionListener(this);
    }
  }

  private static class TextFieldBinding extends AbstactBinding {
    private final JTextField myCommon;
    private List<JTextField> myTextFields;
    private List<String> myInitialValues;
    private DocumentAdapter myListener;

    private TextFieldBinding(final JTextField common, final List<JTextField> textFields) {
      LOG.assertTrue(!textFields.isEmpty());
      myCommon = common;
      myTextFields = textFields;
      String initialValue = myTextFields.get(0).getText();
      myInitialValues = new ArrayList<String>();
      for (JTextField field : myTextFields) {
        String value = field.getText();
        myInitialValues.add(value);
        if (initialValue != null && !initialValue.equals(value)) {
          initialValue = null;
        }
      }
      common.setText(initialValue != null ? initialValue : "");

      myListener = new DocumentAdapter() {
        protected void textChanged(final DocumentEvent e) {
          TextFieldBinding.this.textChanged();
        }
      };
      myCommon.getDocument().addDocumentListener(myListener);
    }

    protected void textChanged() {
      String value = myCommon.getText();
      for (int i = 0; i < myTextFields.size(); i++) {
        myTextFields.get(i).setText(value.length() == 0 ? myInitialValues.get(i) : value);
      }
    }

    public void unbind() {
      myCommon.getDocument().removeDocumentListener(myListener);
    }
  }

  private static class CombobBoxBinding extends AbstactBinding implements ItemListener {
    private final JComboBox myCommon;
    private final List<JComboBox> myComponentsList;
    private List<Object> myInitialValues;

    public CombobBoxBinding(final JComboBox common, final List<JComboBox> componentsList) {
      LOG.assertTrue(!componentsList.isEmpty());
      myCommon = common;
      myComponentsList = componentsList;

      JComboBox first = componentsList.get(0);
      Object initialValue = first.getSelectedItem();

      myInitialValues = new ArrayList<Object>();
      for (JComboBox component : componentsList) {
        Object item = component.getSelectedItem();
        myInitialValues.add(item);
        if (initialValue != null && !initialValue.equals(item)) {
          initialValue = null;
        }
      }
      common.setSelectedItem(initialValue);

      common.addItemListener(this);
    }

    public void unbind() {
      myCommon.removeItemListener(this);
    }

    public void itemStateChanged(final ItemEvent e) {
      Object item = myCommon.getSelectedItem();
      for (int i = 0; i < myComponentsList.size(); i++) {
        myComponentsList.get(i).setSelectedItem(item != null ? item : myInitialValues.get(i));
      }
    }
  }
}
