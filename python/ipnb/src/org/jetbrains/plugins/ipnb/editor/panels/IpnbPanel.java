package org.jetbrains.plugins.ipnb.editor.panels;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCell;

import javax.swing.*;
import java.awt.*;

public abstract class IpnbPanel<T extends JComponent, K extends IpnbCell> extends JPanel {
  protected T myViewPanel;
  protected K myCell;

  public IpnbPanel(@NotNull final K cell) {
    super(new CardLayout());
    myCell = cell;
  }

 public IpnbPanel(@NotNull final K cell, @NotNull final LayoutManager layoutManager) {
    super(layoutManager);
    myCell = cell;
  }

  public K getCell() {
    return myCell;
  }

  protected abstract T createViewPanel();
}
