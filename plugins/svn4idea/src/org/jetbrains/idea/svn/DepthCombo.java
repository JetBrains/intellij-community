// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn;

import com.intellij.ui.SimpleListCellRenderer;
import org.jetbrains.idea.svn.api.Depth;

import javax.swing.*;

import static org.jetbrains.idea.svn.SvnBundle.message;

public class DepthCombo extends JComboBox<Depth> {
  public DepthCombo(final boolean forUpdate) {
    super(forUpdate ? ourForUpdate : ourForCheckout);
    setRenderer(SimpleListCellRenderer.create(
      "",
      value -> Depth.UNKNOWN.equals(value) ? message("label.working.copy") : value.getDisplayName()
    ));
    setSelectedItem(forUpdate ? Depth.UNKNOWN : Depth.INFINITY);
    setEditable(false);
    setToolTipText(message("label.depth.description"));
  }

  public Depth getDepth() {
    return (Depth)super.getSelectedItem();
  }

  private static final Depth[] ourForUpdate = {Depth.UNKNOWN, Depth.EMPTY, Depth.FILES, Depth.IMMEDIATES, Depth.INFINITY};
  private static final Depth[] ourForCheckout = {Depth.EMPTY, Depth.FILES, Depth.IMMEDIATES, Depth.INFINITY};
}
