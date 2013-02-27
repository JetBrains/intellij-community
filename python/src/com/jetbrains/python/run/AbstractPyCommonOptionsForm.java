package com.jetbrains.python.run;

import com.intellij.ui.PanelWithAnchor;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.event.ActionListener;

/**
 * @author yole
 */
public interface AbstractPyCommonOptionsForm extends AbstractPythonRunConfigurationParams, PanelWithAnchor {
  @NonNls String EXPAND_PROPERTY_KEY = "ExpandEnvironmentPanel";

  JComponent getMainPanel();

  void subscribe();

  void addInterpreterComboBoxActionListener(ActionListener listener);

  void removeInterpreterComboBoxActionListener(ActionListener listener);
}
