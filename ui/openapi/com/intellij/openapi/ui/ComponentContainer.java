package com.intellij.openapi.ui;

import com.intellij.openapi.Disposable;

import javax.swing.*;

public interface ComponentContainer extends Disposable {

  JComponent getComponent();
  JComponent getPreferredFocusableComponent();

  
}
