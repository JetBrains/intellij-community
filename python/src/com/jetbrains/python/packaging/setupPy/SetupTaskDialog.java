package com.jetbrains.python.packaging.setupPy;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class SetupTaskDialog extends DialogWrapper {
  private JPanel myOptionsPanel;
  private Map<SetupTaskIntrospector.SetupTaskOption, JComponent> myOptionComponents = new LinkedHashMap<SetupTaskIntrospector.SetupTaskOption, JComponent>();

  protected SetupTaskDialog(Project project, String taskName, List<SetupTaskIntrospector.SetupTaskOption> options) {
    super(project, true);
    myOptionsPanel = new JPanel(new GridBagLayout());
    GridBagConstraints constraints = new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.BOTH,
                                                            new Insets(0, 0, 0, 0), 4, 4);
    for (SetupTaskIntrospector.SetupTaskOption option : options) {
      if (!option.checkbox) {
        addComponent(constraints, option);
      }
    }
    for (SetupTaskIntrospector.SetupTaskOption option : options) {
      if (option.checkbox) {
        addComponent(constraints, option);
      }
    }
    init();
    setTitle("Run Setup Task " + taskName);
  }

  private void addComponent(GridBagConstraints constraints, SetupTaskIntrospector.SetupTaskOption option) {
    JComponent component = createOptionComponent(option);
    myOptionsPanel.add(component, constraints);
    myOptionComponents.put(option, component);
    constraints.gridy++;
  }

  private static JComponent createOptionComponent(SetupTaskIntrospector.SetupTaskOption option) {
    if (option.checkbox) {
      return new JCheckBox(option.description, option.negative);
    }
    return LabeledComponent.create(new JTextField(), option.description);
  }

  @Override
  protected JComponent createCenterPanel() {
    return myOptionsPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    if (myOptionComponents.size() > 0) {
      final JComponent component = myOptionComponents.values().iterator().next();
      return component instanceof LabeledComponent ? ((LabeledComponent) component).getComponent() : component;
    }
    return super.getPreferredFocusedComponent();
  }

  public List<String> getCommandLine() {
    List<String> result = new ArrayList<String>();
    for (Map.Entry<SetupTaskIntrospector.SetupTaskOption, JComponent> entry : myOptionComponents.entrySet()) {
      final SetupTaskIntrospector.SetupTaskOption option = entry.getKey();
      if (option.checkbox) {
        JCheckBox checkBox = (JCheckBox) entry.getValue();
        if (checkBox.isSelected() != option.negative) {
          result.add(option.name);
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
}
