/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.containers;



public class HugeArray {
  private Object[][] myRows;
  private int myRowBits;
  private int myColumnMask;
  private int myMaxIndex = -1;

  public HugeArray(int rowBits) {
    myRowBits = rowBits;
    int rowLength = 1 << rowBits;
    myRows = new Object[rowLength][];
    myColumnMask = rowLength - 1;
  }

  public void put(int index, Object object) {
    int rowIndex = calcRow(index);
    Object[] row = myRows[rowIndex];
    if (row == null) {
      row = new Object[myColumnMask + 1];
      myRows[rowIndex] = row;
    }
    row[calcColumn(index)] = object;
    if (myMaxIndex < index) myMaxIndex = index;
  }

  public Object get(int index) {
    return myRows[calcRow(index)][calcColumn(index)];
  }

  public final int calcColumn(int index) {
    return index & myColumnMask;
  }

  public final int calcRow(int index) {
    return index >> myRowBits;
  }

  public int size() {
    return myMaxIndex + 1;
  }

  public Object[] toArray() {
    return toArray(new Object[size()]);
  }

  public Object[] toArray(Object[] array) {
    int firstIndex = 0;
    final int rowLength = myColumnMask + 1;
    int lastRowToCopy = calcRow(array.length) + (calcColumn(array.length) == 0 ? 0 : 1);
    for (int rowIndex = 0; rowIndex < lastRowToCopy; rowIndex++) {
      System.arraycopy(myRows[rowIndex], 0, array, firstIndex, Math.min(array.length - firstIndex, rowLength));
      firstIndex += rowLength;
    }
    return array;
  }

  public void add(Object object) {
    put(myMaxIndex + 1, object);
  }
}
