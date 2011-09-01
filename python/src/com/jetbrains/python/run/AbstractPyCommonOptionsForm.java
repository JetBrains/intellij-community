package com.jetbrains.python.run;

import com.intellij.ui.ComponentWithAnchor;

import javax.swing.*;

/**
 * @author yole
 */
public interface AbstractPyCommonOptionsForm extends AbstractPythonRunConfigurationParams, ComponentWithAnchor {
  JComponent getMainPanel();
}
