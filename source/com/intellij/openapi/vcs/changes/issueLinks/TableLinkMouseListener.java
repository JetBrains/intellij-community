package com.intellij.openapi.vcs.changes.issueLinks;

import com.intellij.ide.BrowserUtil;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.dualView.DualView;
import com.intellij.ui.dualView.TreeTableView;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

/**
 * @author yole
 */
public class TableLinkMouseListener extends MouseAdapter implements MouseMotionListener {
  public void mouseClicked(final MouseEvent e) {
    if (e.getButton() == 1 && !e.isPopupTrigger()) {
      Object tag = getTagAt(e);
      if (tag != null) {
        BrowserUtil.launchBrowser(tag.toString());
      }
    }
  }

  @Nullable
  private Object getTagAt(final MouseEvent e) {
    // TODO[yole]: don't update renderer on every event, like it's done in TreeLinkMouseListener
    Object tag = null;
    JTable table = (JTable)e.getSource();
    int row = table.rowAtPoint(e.getPoint());
    int column = table.columnAtPoint(e.getPoint());
    TableCellRenderer cellRenderer = table.getCellRenderer(row, column);
    if (cellRenderer instanceof DualView.TableCellRendererWrapper) {
      cellRenderer = ((DualView.TableCellRendererWrapper) cellRenderer).getRenderer();
    }
    if (cellRenderer instanceof TreeTableView.CellRendererWrapper) {
      cellRenderer = ((TreeTableView.CellRendererWrapper) cellRenderer).getBaseRenderer();
    }
    if (cellRenderer instanceof ColoredTableCellRenderer) {
      final ColoredTableCellRenderer renderer = (ColoredTableCellRenderer)cellRenderer;
      renderer.getTableCellRendererComponent(table, table.getValueAt(row, column), false, false, row, column);
      final Rectangle rc = table.getCellRect(row, column, false);
      int index = renderer.findFragmentAt(e.getPoint().x - rc.x);
      if (index >= 0) {
        tag = renderer.getFragmentTag(index);
      }
    }
    return tag;
  }

  public void mouseDragged(MouseEvent e) {
  }

  public void mouseMoved(MouseEvent e) {
    JTable table = (JTable) e.getSource();
    Object tag = getTagAt(e);
    if (tag != null) {
      table.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    else {
      table.setCursor(Cursor.getDefaultCursor());
    }
  }

  public void install(JTable table) {
    table.addMouseListener(this);
    table.addMouseMotionListener(this);
  }
}