package com.intellij.ide.bookmarks;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.util.ui.ItemRemovable;
import com.intellij.util.ui.Table;
import com.intellij.ide.IdeBundle;
import com.intellij.CommonBundle;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.*;

abstract class BookmarksDialog extends DialogWrapper{
  private MyModel myModel;
  protected Table myTable;
  private JButton myGotoButton = new JButton(IdeBundle.message("button.go.to"));
  private JButton myRemoveButton = new JButton(IdeBundle.message("button.remove"));
  private JButton myRemoveAllButton = new JButton(IdeBundle.message("button.remove.all"));
  private JButton myMoveUpButton = new JButton(IdeBundle.message("button.move.up"));
  private JButton myMoveDownButton = new JButton(IdeBundle.message("button.move.down"));
  private JButton myCloseButton = new JButton(CommonBundle.getCloseButtonText());
  protected BookmarkManager myBookmarkManager;

  protected class MyModel extends DefaultTableModel implements ItemRemovable {
    public MyModel() {
      super(new Object[0][], new Object[] {
        IdeBundle.message("column.bookmark"),
        IdeBundle.message("column.description")
      });
    }

    public boolean isCellEditable(int row, int column) {
      return column == 1; // description
    }

    public void setValueAt(Object aValue, int row, int column) {
      if (column == 1) {
        getBookmarkWrapper(row).getBookmark().setDescription((String)aValue);
      }
      super.setValueAt(aValue, row, column);
      myTable.repaint();
    }

    public Object getValueAt(int row, int column) {
      switch (column) {
        case 0:
          return getBookmarkWrapper(row).myDisplayText;
        case 1:
          return getBookmarkWrapper(row).getBookmark().getDescription();
        default:
          return super.getValueAt(row, column);
      }
    }

    public BookmarkWrapper getBookmarkWrapper(int row) {
      return (BookmarkWrapper)super.getValueAt(row, 0);
    }
  }

  BookmarksDialog(BookmarkManager bookmarkManager) {
    super(bookmarkManager.getProject(), true);
    myBookmarkManager = bookmarkManager;
    myModel = new MyModel();
    myTable = new Table(myModel);
  }

  protected Border createContentPaneBorder(){
    return null;
  }

  protected JComponent createSouthPanel(){
    return null;
  }

  protected JComponent createCenterPanel(){
    myTable.setColumnSelectionAllowed(false);
    myTable.setPreferredScrollableViewportSize(new Dimension(500, 200));
    JScrollPane tableScrollPane = ScrollPaneFactory.createScrollPane(myTable);

    myTable.getColumnModel().getColumn(0).setPreferredWidth(400);
    myTable.getColumnModel().getColumn(1).setPreferredWidth(100);

    myTable.addKeyListener(new KeyAdapter() {
      public void keyTyped(KeyEvent e) {
        handle(e);
      }

      public void keyPressed(KeyEvent e) {
        handle(e);
      }

      private void handle(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && e.getModifiers() == 0) {
          myCloseButton.doClick();
        }
        else if (e.getKeyCode() == KeyEvent.VK_ENTER && e.getModifiers() == 0) {
          myGotoButton.doClick();
        }
      }
    });

    JPanel panel=new JPanel(new GridBagLayout());
    GridBagConstraints constr;

    constr = new GridBagConstraints();
    constr.weightx = 1;
    constr.weighty = 1;
    constr.insets = new Insets(5, 5, 5, 0);
    constr.fill = GridBagConstraints.BOTH;
    constr.anchor = GridBagConstraints.WEST;
    panel.add(tableScrollPane, constr);
    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    constr = new GridBagConstraints();
    constr.gridx = 1;
    constr.insets = new Insets(5, 0, 0, 0);
    constr.anchor = GridBagConstraints.NORTH;
    panel.add(createRightButtonPane(), constr);

    addListeners();

    DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
      public Component getTableCellRendererComponent(
        JTable table,
        Object value,
        boolean isSelected,
        boolean hasFocus,
        int row,
        int column
      ) {
        Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        BookmarkWrapper bookmarkWrapper = myModel.getBookmarkWrapper(row);
        setIcon(bookmarkWrapper.getBookmark().getIcon());
        return component;
      }
    };
    myTable.getColumnModel().getColumn(0).setCellRenderer(renderer);

    return panel;
  }

  // buttons
  protected JPanel createRightButtonPane() {
    JPanel pane = new JPanel(new GridBagLayout());

    GridBagConstraints constr = new GridBagConstraints();
    constr.insets = new Insets(0, 5, 5, 5);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.weightx = 1.0;
    pane.add(myGotoButton, constr);
    constr.gridy = 2;
    pane.add(myMoveUpButton, constr);
    constr.gridy = 3;
    pane.add(myMoveDownButton, constr);
    constr.gridy = 4;
    pane.add(myRemoveButton, constr);
    constr.gridy = 5;
    pane.add(myRemoveAllButton, constr);
    constr.gridy = 6;
    pane.add(myCloseButton, constr);

    return pane;
  }

  private static class BookmarkWrapper {
    private Bookmark myBookmark;
    private String myDisplayText;

    BookmarkWrapper(Bookmark bookmark) {
      myBookmark = bookmark;
      myDisplayText = bookmark.toString();
    }

    public String toString() {
      return myDisplayText;
    }

    public Bookmark getBookmark() {
      return myBookmark;
    }
  }

  protected <T extends Bookmark> void fillList(java.util.List<T> bookmarks, Bookmark defaultSelectedBookmark) {
    while (myModel.getRowCount() > 0){
      myModel.removeRow(0);
    }
    for(int i = 0; i < bookmarks.size(); i++){
      Bookmark bookmark = bookmarks.get(i);
      myModel.addRow(new Object[] {new BookmarkWrapper(bookmark), null});
      if (i == 0 || bookmark == defaultSelectedBookmark) {
        myTable.getSelectionModel().setSelectionInterval(i, i);
      }
    }
    final int index = myTable.getSelectionModel().getMinSelectionIndex();
    if (index >= 0) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myTable.scrollRectToVisible(myTable.getCellRect(index, 0, true));
        }
      });
    }
    enableButtons();
  }

  protected void addListeners() {
    myTable.addMouseListener(
      new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2) {
            myGotoButton.doClick();
          }
        }
      }
    );

    myTable.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          enableButtons();
        }
      }
    );

    myGotoButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          stopCellEditing();
          if (getSelectedBookmark() == null) return;
          gotoSelectedBookmark(true);
        }
      }
    );

    myRemoveButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          stopCellEditing();
          removeSelectedBookmark();
          myTable.requestFocus();
        }
      }
    );

    myRemoveAllButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          stopCellEditing();
          while (myModel.getRowCount() > 0) {
            Bookmark bookmark = myModel.getBookmarkWrapper(0).getBookmark();
            myBookmarkManager.removeBookmark(bookmark);
            myModel.removeRow(0);
          }
          myTable.clearSelection();
          enableButtons();
          myTable.requestFocus();
        }
      }
    );

    myMoveUpButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          stopCellEditing();
          Bookmark bookmark = getSelectedBookmark();
          fillList(myBookmarkManager.moveBookmarkUp(bookmark), bookmark);
          myTable.requestFocus();
        }
      }
    );

    myMoveDownButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          stopCellEditing();
          Bookmark bookmark = getSelectedBookmark();
          fillList(myBookmarkManager.moveBookmarkDown(bookmark), bookmark);
          myTable.requestFocus();
        }
      }
    );

    myCloseButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          close(CANCEL_EXIT_CODE);
        }
      }
    );

    ShortcutSet shortcutSet = ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet();
    new AnAction() {
      public void actionPerformed(AnActionEvent e){
        myGotoButton.doClick();
      }
    }.registerCustomShortcutSet(shortcutSet, getRootPane());

    getRootPane().registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          removeSelectedBookmark();
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
      JComponent.WHEN_IN_FOCUSED_WINDOW
    );

    getRootPane().setDefaultButton(myGotoButton);
  }

  private void removeSelectedBookmark() {
    Bookmark selectedBookmark = getSelectedBookmark();
    if (selectedBookmark != null){
      myBookmarkManager.removeBookmark(selectedBookmark);
      TableUtil.removeSelectedItems(myTable);
      enableButtons();
    }
  }

  protected void enableButtons() {
    int selectedIndex = myTable.getSelectionModel().getMinSelectionIndex();
    myRemoveButton.setEnabled(selectedIndex != -1);
    myRemoveAllButton.setEnabled(myModel.getRowCount() > 0);
    myGotoButton.setEnabled(selectedIndex != -1);
    myMoveUpButton.setEnabled(selectedIndex > 0);
    myMoveDownButton.setEnabled(selectedIndex != -1 && selectedIndex < myModel.getRowCount() - 1);
  }

  abstract protected void gotoSelectedBookmark(boolean closeWindow);

  protected Bookmark getSelectedBookmark() {
    int selectedIndex = myTable.getSelectionModel().getMinSelectionIndex();
    if (selectedIndex == -1 || selectedIndex >= myModel.getRowCount()) return null;
    return myModel.getBookmarkWrapper(selectedIndex).getBookmark();
  }

  public void dispose() {
    stopCellEditing();
    super.dispose();
  }

  private void stopCellEditing() {
    if (myTable.isEditing()) {
      TableCellEditor editor = myTable.getCellEditor();
      if (editor != null) {
        editor.stopCellEditing();
      }
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }
}