/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.ui;

import javax.swing.table.AbstractTableModel;
import java.util.List;

public abstract class TableViewModel<Item> extends AbstractTableModel implements SortableColumnModel {
  public abstract void setItems(List<Item> items);
  public abstract List<Item> getItems();
}