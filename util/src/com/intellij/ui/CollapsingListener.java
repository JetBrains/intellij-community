/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.ui;

public interface CollapsingListener {
  void onCollapsingChanged(CollapsiblePanel panel, boolean newValue);
}
