package com.intellij.uiDesigner.palette;

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicListUI;
import java.awt.*;
import java.awt.event.*;

/**
 * @author yole
 */
public class PaletteComponentList extends JList {
  private GroupItem myGroupItem;
  private int myHoverIndex = -1;

  public PaletteComponentList(final GroupItem groupItem) {
    myGroupItem = groupItem;
    setModel(new AbstractListModel() {
      public int getSize() {
        return myGroupItem.getItems().size();
      }

      public Object getElementAt(int index) {
        return myGroupItem.getItems().get(index);
      }
    });

    addMouseListener(new MouseAdapter() {
      @Override public void mouseEntered(MouseEvent e) {
        setHoverIndex(locationToIndex(e.getPoint()));
      }

      @Override public void mouseExited(MouseEvent e) {
        setHoverIndex(-1);
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

  public void takeFocusFrom(PaletteGroup paletteGroup, int indexToSelect) {
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

  static class ComponentListUI extends BasicListUI {
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
  }

  private class ComponentCellRenderer extends ColoredListCellRenderer {
    private Border myHoverBorder = BorderFactory.createLineBorder(Color.BLUE);

    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      ComponentItem componentItem = (ComponentItem) value;
      clear();
      setBorder(index == myHoverIndex ? myHoverBorder : null);
      setIcon(componentItem.getSmallIcon());
      append(componentItem.getClassShortName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      setToolTipText(componentItem.getToolTipText());
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
      if (null != next && next instanceof PaletteGroup) {
        clearSelection();
        next.requestFocus();
        ((PaletteGroup)next).scrollRectToVisible(next.getBounds());
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
}
