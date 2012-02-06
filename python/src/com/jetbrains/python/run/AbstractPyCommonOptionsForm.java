package com.jetbrains.python.run;

import com.intellij.ui.PanelWithAnchor;

import javax.swing.*;
import java.awt.event.ActionListener;

/**
 * @author yole
 */
public interface AbstractPyCommonOptionsForm extends AbstractPythonRunConfigurationParams, PanelWithAnchor {
  JComponent getMainPanel();

  void subscribe();

  void addInterpreterComboBoxActionListener(ActionListener listener);

  void removeInterpreterComboBoxActionListener(ActionListener listener);
}
