// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.palette.impl;

import com.intellij.ide.dnd.*;
import com.intellij.ide.palette.PaletteGroup;
import com.intellij.ide.palette.PaletteItem;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListActions;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.PlatformColors;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.basic.BasicListUI;
import java.awt.*;
import java.awt.event.*;


public final class PaletteComponentList extends JBList {
  private final Project myProject;
  private final PaletteWindow myPalette;
  private final PaletteGroup myGroup;
  private int myHoverIndex = -1;
  private int myBeforeClickSelectedRow = -1;
  private int myDropTargetIndex = -1;
  private boolean myNeedClearSelection = false;

  public PaletteComponentList(Project project, PaletteWindow palette, PaletteGroup group) {
    myProject = project;
    myPalette = palette;
    myGroup = group;
    setModel(new AbstractListModel() {
      @Override
      public int getSize() {
        return myGroup.getItems().length;
      }

      @Override
      public Object getElementAt(int index) {
        return myGroup.getItems() [index];
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
                                !UIUtil.isControlKeyDown(e) && !e.isShiftDown());
      }

      @Override public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) &&
            myBeforeClickSelectedRow >= 0 &&
            locationToIndex(e.getPoint()) == myBeforeClickSelectedRow &&
            !UIUtil.isControlKeyDown(e) && !e.isShiftDown() && myNeedClearSelection) {
          clearSelection();
        }
      }
    });

    addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(final Component comp, final int x, final int y) {
        requestFocusInWindow();
        SwingUtilities.invokeLater(() -> {
          int index = locationToIndex(new Point(x, y));
          PaletteItem[] items = myGroup.getItems();
          if (index >= 0 && index < items.length) {
            if (getSelectedIndex() != index) {
              addSelectionInterval(index, index);
            }
            PaletteItem item = items [index];
            ActionGroup group1 = item.getPopupActionGroup();
            if (group1 != null) {
              ActionPopupMenu popupMenu1 = ActionManager.getInstance().createActionPopupMenu("PaletteComponentList", group1);
              popupMenu1.getComponent().show(comp, x, y);
            }
          }
        });
      }
    });

    addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        setHoverIndex(locationToIndex(e.getPoint()));
      }
    });

    addKeyListener(new KeyListener() {
      @Override
      public void keyPressed(KeyEvent e) {
        myPalette.notifyKeyEvent(e);
      }

      @Override
      public void keyReleased(KeyEvent e) {
        myPalette.notifyKeyEvent(e);
      }

      @Override
      public void keyTyped(KeyEvent e) {
        myPalette.notifyKeyEvent(e);
      }
    });

    setCellRenderer(new ComponentCellRenderer());

    setVisibleRowCount(0);
    setLayoutOrientation(HORIZONTAL_WRAP);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    final DnDManager dndManager = DnDManager.getInstance();
    dndManager.registerSource(new MyDnDSource(), this);
    dndManager.registerTarget(new MyDnDTarget(), this);

    initActions();
  }

  private void setHoverIndex(final int index) {
    if (index != myHoverIndex) {
      if (myHoverIndex >= 0) repaint(getCellBounds(myHoverIndex, myHoverIndex));
      myHoverIndex = index;
      if (myHoverIndex >= 0) repaint(getCellBounds(myHoverIndex, myHoverIndex));
    }
  }

  private void setDropTargetIndex(final int index) {
    if (index != myDropTargetIndex) {
      myDropTargetIndex = index;
      repaint();
    }
  }

  @Override public void updateUI() {
    setUI(new ComponentListUI());
    invalidate();
  }

  private void initActions() {
    @NonNls ActionMap map = getActionMap();
    map.put(ListActions.Up.ID, new MoveFocusAction(map.get(ListActions.Up.ID), false));
    map.put(ListActions.Down.ID, new MoveFocusAction(map.get(ListActions.Down.ID), true));
    map.put(ListActions.Left.ID, new MoveFocusAction(new ChangeColumnAction(map.get(ListActions.Left.ID), false), false));
    map.put(ListActions.Right.ID, new MoveFocusAction(new ChangeColumnAction(map.get(ListActions.Right.ID), true), true));
  }

  Integer myTempWidth;

  @Override
  public int getWidth () {
      return (myTempWidth == null) ? super.getWidth () : myTempWidth.intValue ();
  }

  public int getPreferredHeight(final int width) {
    myTempWidth = width;
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
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(this, true));
    setSelectedIndex(indexToSelect);
    if (indexToSelect >= 0) {
      ensureIndexIsVisible(indexToSelect);
    }
  }

  @Override protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (myDropTargetIndex >= 0) {
      int dropLineY;
      Rectangle rc;
      if (myDropTargetIndex == myGroup.getItems().length) {
        rc = getCellBounds(myDropTargetIndex-1, myDropTargetIndex-1);
        dropLineY = (int)rc.getMaxY()-1;
      }
      else {
        rc = getCellBounds(myDropTargetIndex, myDropTargetIndex);
        dropLineY = rc.y;
      }
      Graphics2D g2d = (Graphics2D) g;
      g2d.setColor(PlatformColors.BLUE);
      g2d.setStroke(new BasicStroke(2.0f));
      g2d.drawLine(rc.x, dropLineY, rc.x+rc.width, dropLineY);
      g2d.drawLine(rc.x, dropLineY-2, rc.x, dropLineY+2);
      g2d.drawLine(rc.x+rc.width, dropLineY-2, rc.x+rc.width, dropLineY+2);
    }
  }

  class ComponentListUI extends BasicListUI {
    private ComponentListListener myListener;

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
      myListener = new ComponentListListener();
      addMouseListener(myListener);
      super.installListeners();
    }

    @Override
    protected void uninstallListeners() {
      if (myListener != null) {
        removeMouseListener(myListener);
      }
      super.uninstallListeners();
    }

    private class ComponentListListener extends MouseAdapter {
      @Override public void mousePressed(MouseEvent e) {
        myBeforeClickSelectedRow = list.getSelectedIndex();
      }
    }
  }

  private static class ComponentCellRenderer extends ColoredListCellRenderer {
    @Override
    protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
      PaletteItem paletteItem = (PaletteItem) value;
      clear();
      paletteItem.customizeCellRenderer(this, selected, hasFocus);
    }
  }

  private class MoveFocusAction extends AbstractAction {
    private final Action defaultAction;
    private final boolean focusNext;

    MoveFocusAction(Action defaultAction, boolean focusNext) {
      this.defaultAction = defaultAction;
      this.focusNext = focusNext;
    }

    @Override
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
      if (next instanceof PaletteGroupHeader) {
        clearSelection();
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(next, true));
        ((PaletteGroupHeader)next).scrollRectToVisible(next.getBounds());
      }
    }
  }

  private class ChangeColumnAction extends AbstractAction {
    private final Action defaultAction;
    private final boolean selectNext;

    ChangeColumnAction(Action defaultAction, boolean selectNext) {
      this.defaultAction = defaultAction;
      this.selectNext = selectNext;
    }

    @Override
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

  private class MyDnDTarget implements DnDTarget {

    @Override
    public boolean update(DnDEvent aEvent) {
      setHoverIndex(-1);
      if (aEvent.getAttachedObject() instanceof PaletteItem) {
        setDropTargetIndex(locationToTargetIndex(aEvent.getPoint()));
        aEvent.setDropPossible(true);
      }
      else {
        setDropTargetIndex(-1);
        aEvent.setDropPossible(false);
      }
      return false;
    }

    @Override
    public void drop(DnDEvent aEvent) {
      setDropTargetIndex(-1);
      if (aEvent.getAttachedObject() instanceof PaletteItem) {
        int index = locationToTargetIndex(aEvent.getPoint());
        if (index >= 0) {
          myGroup.handleDrop(myProject, (PaletteItem) aEvent.getAttachedObject(), index);
        }
      }
    }

    @Override
    public void cleanUpOnLeave() {
      setDropTargetIndex(-1);
    }

    private int locationToTargetIndex(Point location) {
      int row = locationToIndex(location);
      if (row < 0) {
        return -1;
      }
      Rectangle rc = getCellBounds(row, row);
      return location.y < rc.getCenterY() ? row : row + 1;
    }
  }

  private class MyDnDSource implements DnDSource {
    @Override
    public boolean canStartDragging(DnDAction action, @NotNull Point dragOrigin) {
      int index = locationToIndex(dragOrigin);
      return index >= 0 && myGroup.getItems() [index].startDragging() != null;
    }

    @Override
    public DnDDragStartBean startDragging(DnDAction action, @NotNull Point dragOrigin) {
      int index = locationToIndex(dragOrigin);
      if (index < 0) return null;
      return myGroup.getItems() [index].startDragging();
    }

    @Override
    public void dropActionChanged(final int gestureModifiers) {
      myPalette.notifyDropActionChanged(gestureModifiers);
    }
  }
}
