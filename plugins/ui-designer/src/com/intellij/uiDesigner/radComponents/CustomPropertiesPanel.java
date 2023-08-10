// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.radComponents;

import javax.swing.*;
import javax.swing.event.ChangeListener;


public interface CustomPropertiesPanel {
  JComponent getComponent();
  void addChangeListener(ChangeListener listener);
  void removeChangeListener(ChangeListener listener);
}
