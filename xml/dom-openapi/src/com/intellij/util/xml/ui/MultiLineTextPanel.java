// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.ui;

public final class MultiLineTextPanel extends TextPanel {
  private int rowCount = 3;

  public int getRowCount() {
    return rowCount;
  }

  public void setRowCount(final int rowCount) {
    this.rowCount = rowCount;
  }
}
