package com.jetbrains.edu.learning.settings;


import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface ModifiableSettingsPanel {
  void apply();

  void reset();

  void resetCredentialsModification();

  boolean isModified();

  @NotNull
  JComponent getPanel();
}
