/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.ui;

import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public abstract class ColumnInfo <Item, Aspect> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ui.ColumnInfo");
  private String myName;

  public ColumnInfo(String name) {
    myName = name;
  }

  public String toString() {
    return getName();
  }

  public abstract Aspect valueOf(Item object);

  public Comparator<Item> getComparator(){
    return null;
  }

  public String getName() {
    return myName;
  }

  public void sort(List<Item> list) {
    LOG.assertTrue(list != null);
    Comparator<Item> comparator = getComparator();
    if (comparator != null) Collections.sort(list, comparator);
  }

  public Class getColumnClass() {
    return String.class;
  }

  public boolean isCellEditable(Item o) {
    return false;
  }

  public void setValue(Item o, Aspect aValue) {

  }

  public TableCellRenderer getRenderer(Item p0) {
    return null;
  }

  public TableCellEditor getEditor(Item item) {
    return null;
  }

  public String getMaxStringValue() {
    return null;
  }

  public int getAdditionalWidth() {
    return 0;
  }

  public int getWidth(JTable table) {
    return -1;
  }

  public void setName(String s) {
    myName = s;
  }
}
