package org.jetbrains.plugins.ipnb.editor.panels;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCell;

import javax.swing.*;
import java.awt.*;

public abstract class IpnbPanel<T extends JComponent, K extends IpnbCell> extends JPanel {
  protected T myViewPanel;
  protected final K myCell;

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

  public ListPopup createPopupMenu(@NotNull DefaultActionGroup group) {
    final DataContext context = DataManager.getInstance().getDataContext(this);
    return JBPopupFactory.getInstance().createActionGroupPopup(null, group, context, JBPopupFactory.ActionSelectionAid.MNEMONICS,
                                                               false);
  }

  protected abstract T createViewPanel();
  
  protected abstract void addRightClickMenu();
}
