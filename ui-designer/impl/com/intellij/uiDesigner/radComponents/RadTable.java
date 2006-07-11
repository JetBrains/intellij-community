/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.palette.Palette;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 * @author yole
 */
public class RadTable extends RadAtomicComponent {
  public RadTable(final Module module, final Class componentClass, final String id) {
    super(module, componentClass, id);
    initDefaultModel();
  }

  public RadTable(final Class componentClass, final String id, final Palette palette) {
    super(componentClass, id, palette);
    initDefaultModel();
  }

  private void initDefaultModel() {
    @NonNls Object[][] data = new Object[][] { new Object[] { "round", "red"},
      new Object[] { "square", "green" } };
    @NonNls Object[] columnNames = new Object[] { "Shape", "Color" };
    ((JTable) getDelegee()).setModel(new DefaultTableModel(data, columnNames));
  }
}
