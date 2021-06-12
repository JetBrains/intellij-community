// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.ui.PanelWithAnchor;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.util.function.Consumer;


public interface AbstractPyCommonOptionsForm extends AbstractPythonRunConfigurationParams, PanelWithAnchor {
  @NonNls String EXPAND_PROPERTY_KEY = "ExpandEnvironmentPanel";

  JComponent getMainPanel();

  void subscribe();

  void addInterpreterComboBoxActionListener(ActionListener listener);

  void removeInterpreterComboBoxActionListener(ActionListener listener);

  void addInterpreterModeListener(Consumer<Boolean> listener);
}
