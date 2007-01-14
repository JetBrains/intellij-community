package com.intellij.ui;

import javax.swing.*;

public interface ActiveComponent {

  void setActive(boolean active);

  JComponent getComponent();

}
