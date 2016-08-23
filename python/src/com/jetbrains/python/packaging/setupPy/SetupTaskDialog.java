/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.packaging.setupPy;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class SetupTaskDialog extends DialogWrapper {
  private static final String CARD_OPTIONS = "Options";
  private static final String CARD_COMMAND_LINE = "CommandLine";
  private static final String CURRENT_CARD_PROPERTY = "SetupTaskDialog.currentCard";
  private String myCurrentCard;

  private JPanel myMainPanel;
  private JButton myExpandCollapseButton;
  private JPanel myOptionsPanel;
  private Map<SetupTask.Option, JComponent> myOptionComponents = new LinkedHashMap<>();
  private JTextField myCommandLineField;
  private final LabeledComponent<JTextField> myCommandLinePanel;

  protected SetupTaskDialog(Project project, String taskName, List<SetupTask.Option> options) {
    super(project, true);
    myMainPanel = new JPanel(new GridBagLayout());

    myOptionsPanel = new JPanel(new GridBagLayout());
    GridBagConstraints constraints = new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.BOTH,
                                                            new Insets(0, 0, 0, 0), 4, 4);
    for (SetupTask.Option option : options) {
      if (!option.checkbox) {
        addComponent(constraints, option);
      }
    }
    for (SetupTask.Option option : options) {
      if (option.checkbox) {
        addComponent(constraints, option);
      }
    }

    myCommandLineField = new JTextField(50);
    myCommandLinePanel = LabeledComponent.create(myCommandLineField, "Command Line");

    myExpandCollapseButton = new JButton("<< Collapse Options");
    myExpandCollapseButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myCurrentCard.equals(CARD_OPTIONS)) {
          showCommandLine();
        }
        else {
          showOptions();
        }
        pack();
      }
    });
    myMainPanel.add(myExpandCollapseButton, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.VERTICAL,
                                                                   new Insets(8, 0, 0, 0), 0, 0));
    if (CARD_OPTIONS.equals(PropertiesComponent.getInstance().getValue(CURRENT_CARD_PROPERTY))) {
      showOptions();
    }
    else {
      showCommandLine();
    }

    init();
    setTitle("Run Setup Task " + taskName);
  }

  private void showOptions() {
    myCurrentCard = CARD_OPTIONS;
    myMainPanel.remove(myCommandLinePanel);
    myMainPanel.add(myOptionsPanel, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.BOTH,
                                                           new Insets(0, 0, 0, 0), 4, 4));
    myExpandCollapseButton.setText("<< Collapse Options");
  }

  private void showCommandLine() {
    myCurrentCard = CARD_COMMAND_LINE;
    myMainPanel.remove(myOptionsPanel);
    myMainPanel.add(myCommandLinePanel, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.BOTH,
                                                               new Insets(2, 0, 0, 0), 4, 4));
    myCommandLineField.setText(StringUtil.join(getCommandLine(), " "));
    myExpandCollapseButton.setText("Expand Options >>");
  }

  private void addComponent(GridBagConstraints constraints, SetupTask.Option option) {
    JComponent component = createOptionComponent(option);
    myOptionsPanel.add(component, constraints);
    myOptionComponents.put(option, component);
    constraints.gridy++;
  }

  private static JComponent createOptionComponent(SetupTask.Option option) {
    if (option.checkbox) {
      return new JCheckBox(option.description, option.negative);
    }
    return LabeledComponent.create(new JTextField(50), option.description);
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    if (myCurrentCard.equals(CARD_OPTIONS)) {
      if (myOptionComponents.size() > 0) {
        final JComponent component = myOptionComponents.values().iterator().next();
        return component instanceof LabeledComponent ? ((LabeledComponent)component).getComponent() : component;
      }
      return super.getPreferredFocusedComponent();
    }
    return myCommandLineField;
  }

  public List<String> getCommandLine() {
    if (myCurrentCard.equals(CARD_COMMAND_LINE)) {
      return StringUtil.split(myCommandLineField.getText(), " ");
    }
    List<String> result = new ArrayList<>();
    for (Map.Entry<SetupTask.Option, JComponent> entry : myOptionComponents.entrySet()) {
      final SetupTask.Option option = entry.getKey();
      if (option.checkbox) {
        JCheckBox checkBox = (JCheckBox)entry.getValue();
        if (checkBox.isSelected() != option.negative) {
          result.add("--" + option.name);
        }
      }
      else {
        LabeledComponent<JTextField> textField = (LabeledComponent<JTextField>)entry.getValue();
        String text = textField.getComponent().getText();
        if (text.length() > 0) {
          result.add("--" + option.name + text);
        }
      }
    }
    return result;
  }

  @Override
  protected void doOKAction() {
    PropertiesComponent.getInstance().setValue(CURRENT_CARD_PROPERTY, myCurrentCard);
    super.doOKAction();
  }
}
