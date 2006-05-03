/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;import javax.swing.*;import javax.swing.event.ChangeListener;

/**
 * @author yole
 */
public interface RowColumnPropertiesPanel {
  JComponent getComponent();
  void addChangeListener(ChangeListener listener);
  void removeChangeListener(ChangeListener listener);
}
