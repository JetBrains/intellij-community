/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.ui;




public interface SortableColumnModel {
  int SORT_ASCENDING = 1;
  int SORT_DESCENDING = 2;

  ColumnInfo[] getColumnInfos();

  void sortByColumn(int columnIndex);

  int getSortedColumnIndex();

  int getSortingType();

  void setSortable(boolean aBoolean);

  boolean isSortable();
}
