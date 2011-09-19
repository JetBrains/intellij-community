package com.jetbrains.python.run;

import com.intellij.ui.PanelWithAnchor;

import javax.swing.*;

/**
 * @author yole
 */
public interface AbstractPyCommonOptionsForm extends AbstractPythonRunConfigurationParams, PanelWithAnchor {
  JComponent getMainPanel();
}
