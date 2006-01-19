package com.intellij.ide.palette.impl;

import com.intellij.ide.palette.PaletteGroup;
import com.intellij.ide.palette.PaletteItem;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.PopupHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicListUI;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/**
 * @author yole
 */
public class PaletteComponentList extends JList {
  private PaletteGroup myGroup;
  private int myHoverIndex = -1;
  private int myBeforeClickSelectedRow = -1;
  private boolean myNeedClearSelection = false;

  public PaletteComponentList(PaletteGroup group) {
    myGroup = group;
    setModel(new AbstractListModel() {
      public int getSize() {
        return myGroup.getItemCount();
      }

      public Object getElementAt(int index) {
        return myGroup.getItemAt(index);
      }
    });

    addMouseListener(new MouseAdapter() {
      @Override public void mouseEntered(MouseEvent e) {
        setHoverIndex(locationToIndex(e.getPoint()));
      }

      @Override public void mouseExited(MouseEvent e) {
        setHoverIndex(-1);
      }

      @Override public void mousePressed(MouseEvent e) {
        myNeedClearSelection = (SwingUtilities.isLeftMouseButton(e) &&
                                myBeforeClickSelectedRow >= 0 &&
                                locationToIndex(e.getPoint()) == myBeforeClickSelectedRow &&
                                !e.isControlDown() && !e.isShiftDown());
      }

      @Override public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) &&
            myBeforeClickSelectedRow >= 0 &&
            locationToIndex(e.getPoint()) == myBeforeClickSelectedRow &&
            !e.isControlDown() && !e.isShiftDown() && myNeedClearSelection) {
          clearSelection();
        }
      }
    });

    addMouseListener(new PopupHandler() {
      public void invokePopup(final Component comp, final int x, final int y) {
        requestFocusInWindow();
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            int index = locationToIndex(new Point(x, y));
            if (index >= 0 && index < myGroup.getItemCount()) {
              if (getSelectedIndex() != index) {
                addSelectionInterval(index, index);
              }
              PaletteItem item = myGroup.getItemAt(index);
              ActionGroup group = item.getPopupActionGroup();
              if (group != null) {
                ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, group);
                popupMenu.getComponent().show(comp, x, y);
              }
            }
          }
        });
      }
    });

    addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseMoved(MouseEvent e) {
        setHoverIndex(locationToIndex(e.getPoint()));
      }
    });

    setCellRenderer(new ComponentCellRenderer());

    setVisibleRowCount(0);
    setLayoutOrientation(HORIZONTAL_WRAP);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setDragEnabled(true);
    setTransferHandler(new TransferHandler() {
      public int getSourceActions(JComponent c) {
        return DnDConstants.ACTION_MOVE;
      }

      @Nullable protected Transferable createTransferable(JComponent c) {
        final Object selectedValue = getSelectedValue();
        if (selectedValue != null) {
          PaletteItem paletteItem = (PaletteItem) selectedValue;
          return paletteItem.createTransferable();
        }
        return null;
      }
    });

    new DropTarget(this, DnDConstants.ACTION_MOVE, new MyDropTargetAdapter());

    initActions();
  }

  private void setHoverIndex(final int index) {
    if (index != myHoverIndex) {
      if (myHoverIndex >= 0) repaint(getCellBounds(myHoverIndex, myHoverIndex));
      myHoverIndex = index;
      if (myHoverIndex >= 0) repaint(getCellBounds(myHoverIndex, myHoverIndex));
    }
  }

  @Override public void updateUI() {
    setUI(new ComponentListUI());
    invalidate();
  }

  private void initActions() {
    @NonNls ActionMap map = getActionMap();
    map.put( "selectPreviousRow", new MoveFocusAction( map.get( "selectPreviousRow" ), false ) );
    map.put( "selectNextRow", new MoveFocusAction( map.get( "selectNextRow" ), true ) );
    map.put( "selectPreviousColumn", new MoveFocusAction( new ChangeColumnAction( map.get( "selectPreviousColumn" ), false ), false ) );
    map.put( "selectNextColumn", new MoveFocusAction( new ChangeColumnAction( map.get( "selectNextColumn" ), true ), true ) );
  }

  Integer myTempWidth;

  public int getWidth () {
      return (myTempWidth == null) ? super.getWidth () : myTempWidth.intValue ();
  }

  public int getPreferredHeight(final int width) {
    myTempWidth = new Integer(width);
    try {
      return getUI().getPreferredSize(this).height;
    }
    finally {
      myTempWidth = null;
    }
  }

  public void takeFocusFrom(PaletteGroupHeader paletteGroup, int indexToSelect) {
    if (indexToSelect == -1) {
      //this is not 'our' CategoryButton so we'll assume it's the one below this category list
      indexToSelect = getModel().getSize() - 1;
    }
    else if (getModel().getSize() == 0) {
      indexToSelect = -1;
    }
    requestFocus();
    setSelectedIndex(indexToSelect);
    if (indexToSelect >= 0) {
      ensureIndexIsVisible(indexToSelect);
    }
  }

  class ComponentListUI extends BasicListUI {
    @Override protected void updateLayoutState() {
      super.updateLayoutState();

      if (list.getLayoutOrientation() == JList.HORIZONTAL_WRAP) {
        Insets insets = list.getInsets();
        int listWidth = list.getWidth() - (insets.left + insets.right);
        if (listWidth >= cellWidth) {
          int columnCount = listWidth / cellWidth;
          cellWidth = (columnCount == 0) ? 1 : listWidth / columnCount;
        }
      }
    }

    @Override protected void installListeners() {
      addMouseListener(new MouseAdapter() {
        @Override public void mousePressed(MouseEvent e) {
          myBeforeClickSelectedRow = list.getSelectedIndex();
        }
      });
      super.installListeners();
    }
  }

  private class ComponentCellRenderer extends ColoredListCellRenderer {
    private Border myHoverBorder = BorderFactory.createLineBorder(Color.BLUE);

    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      PaletteItem paletteItem = (PaletteItem) value;
      clear();
      setBorder(index == myHoverIndex ? myHoverBorder : null);
      paletteItem.customizeCellRenderer(this, selected, hasFocus);
    }
  }

  private class MoveFocusAction extends AbstractAction {
    private Action defaultAction;
    private boolean focusNext;

    public MoveFocusAction(Action defaultAction, boolean focusNext) {
      this.defaultAction = defaultAction;
      this.focusNext = focusNext;
    }

    public void actionPerformed(ActionEvent e) {
      int selIndexBefore = getSelectedIndex();
      defaultAction.actionPerformed(e);
      int selIndexCurrent = getSelectedIndex();
      if (selIndexBefore != selIndexCurrent) return;

      if (focusNext && 0 == selIndexCurrent) return;

      KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      Container container = kfm.getCurrentFocusCycleRoot();
      FocusTraversalPolicy policy = container.getFocusTraversalPolicy();
      if (null == policy) policy = kfm.getDefaultFocusTraversalPolicy();
      Component next = focusNext
                       ? policy.getComponentAfter(container, PaletteComponentList.this)
                       : policy.getComponentBefore(container, PaletteComponentList.this);
      if (null != next && next instanceof PaletteGroupHeader) {
        clearSelection();
        next.requestFocus();
        ((PaletteGroupHeader)next).scrollRectToVisible(next.getBounds());
      }
    }
  }

  private class ChangeColumnAction extends AbstractAction {
    private Action defaultAction;
    private boolean selectNext;

    public ChangeColumnAction(Action defaultAction, boolean selectNext) {
      this.defaultAction = defaultAction;
      this.selectNext = selectNext;
    }

    public void actionPerformed(ActionEvent e) {
      int selIndexBefore = getSelectedIndex();
      defaultAction.actionPerformed(e);
      int selIndexCurrent = getSelectedIndex();
      if ((selectNext && selIndexBefore < selIndexCurrent) || (!selectNext && selIndexBefore > selIndexCurrent)) return;

      if (selectNext) {
        if (selIndexCurrent == selIndexBefore + 1) selIndexCurrent++;
        if (selIndexCurrent < getModel().getSize() - 1) {
          setSelectedIndex(selIndexCurrent + 1);
          scrollRectToVisible(getCellBounds(selIndexCurrent + 1, selIndexCurrent + 1));
        }
      }
      else {
        if (selIndexCurrent > 0) {
          setSelectedIndex(selIndexCurrent - 1);
          scrollRectToVisible(getCellBounds(selIndexCurrent - 1, selIndexCurrent - 1));
        }
      }
    }
  }

  private class MyDropTargetAdapter extends DropTargetAdapter {
    @Override public void dragOver(DropTargetDragEvent dtde) {
      setHoverIndex(-1);
    }

    public void drop(DropTargetDropEvent dtde) {
    }
  }
}
