package org.jetbrains.idea.svn.difftool.properties;

import com.intellij.ui.table.TableView;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.MouseEvent;

public class PropertiesTableView extends TableView<SvnPropertiesDiffViewer.PropertyDiffRecord> {
  public PropertiesTableView(@NotNull PropertiesTableModel model) {
    super(model);
  }

  private boolean myDuringResizing;

  @Override
  protected void processMouseEvent(MouseEvent e) {
    preProcessMouseEvent(e);
    if (!e.isConsumed()) {
      super.processMouseEvent(e);
    }
  }

  @Override
  protected void processMouseMotionEvent(MouseEvent e) {
    preProcessMouseEvent(e);
    if (!e.isConsumed()) {
      super.processMouseMotionEvent(e);
    }
  }

  private void preProcessMouseEvent(MouseEvent e) {
    Point point = e.getPoint();
    int column = columnAtPoint(point);
    int eventId = e.getID();
    boolean isNameColumn = column == 1;

    if (eventId == MouseEvent.MOUSE_DRAGGED) {
      if (myDuringResizing) {
        resizeValueColumns(point.getX(), getWidth());
        e.consume();
      }
    }
    else if (eventId == MouseEvent.MOUSE_MOVED || eventId == MouseEvent.MOUSE_ENTERED || eventId == MouseEvent.MOUSE_EXITED) {
      updateCursor(isNameColumn);
    }
    else if (eventId == MouseEvent.MOUSE_CLICKED) {
      if (isNameColumn && e.getClickCount() == 2) {
        resizeValueColumns(0.5d);
        e.consume();
      }
    }
    else if (eventId == MouseEvent.MOUSE_PRESSED) {
      if (isNameColumn) {
        myDuringResizing = true;
      }
    }
    else if (eventId == MouseEvent.MOUSE_RELEASED) {
      myDuringResizing = false;
    }
  }

  private void resizeValueColumns(double x, int width) {
    TableColumnModel model = getColumnModel();
    TableColumn left = model.getColumn(0);
    TableColumn name = model.getColumn(1);
    TableColumn right = model.getColumn(2);

    int nameWidth = name.getWidth();
    int newLeftWidth = (int)(x - nameWidth / 2);
    int newRightWidth = width - newLeftWidth - nameWidth;

    left.setPreferredWidth(newLeftWidth);
    right.setPreferredWidth(newRightWidth);
  }

  private void resizeValueColumns(double ratio) {
    TableColumnModel model = getColumnModel();
    TableColumn left = model.getColumn(0);
    TableColumn right = model.getColumn(2);

    ratio = Math.max(0.0d, Math.min(1.0d, ratio));

    int totalWidth = left.getWidth() + right.getWidth();
    int newLeftWidth = (int)(ratio * totalWidth);
    int newRightWidth = totalWidth - newLeftWidth;

    left.setPreferredWidth(newLeftWidth);
    right.setPreferredWidth(newRightWidth);
  }

  private void updateCursor(boolean resize) {
    int newCursorType = resize ? Cursor.W_RESIZE_CURSOR : Cursor.DEFAULT_CURSOR;
    if (getCursor().getType() != newCursorType) {
      setCursor(Cursor.getPredefinedCursor(newCursorType));
    }
  }
}
