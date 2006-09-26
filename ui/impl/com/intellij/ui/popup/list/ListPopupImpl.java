/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.popup.list;

import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.popup.BasePopup;
import com.intellij.ui.popup.PopupIcons;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class ListPopupImpl extends BasePopup implements ListPopup {

  private MyList myList;

  private MyMouseMotionListener myMouseMotionListener;
  private MyMouseListener myMouseListener;

  private ListPopupModel myListModel;

  private int myIndexForShowingChild = -1;
  private int myMaxRowCount = 20;

  public ListPopupImpl(ListPopupStep aStep, int maxRowCount) {
    super(aStep);
    if (maxRowCount != -1){
      myMaxRowCount = maxRowCount;
    }
  }

  public ListPopupImpl(ListPopupStep aStep) {
    super(aStep);
  }

  public ListPopupImpl(BasePopup aParent, ListPopupStep aStep, Object parentValue) {
    super(aParent, aStep);
    setParentValue(parentValue);
  }

  public ListPopupImpl(BasePopup aParent, ListPopupStep aStep, Object parentValue, int maxRowCount) {
    super(aParent, aStep);
    setParentValue(parentValue);
    if (maxRowCount != -1){
      myMaxRowCount = maxRowCount;
    }
  }

  public ListPopupModel getListModel() {
    return myListModel;
  }

  protected void beforeShow() {
    myList.addMouseMotionListener(myMouseMotionListener);
    myList.addMouseListener(myMouseListener);

    myList.setVisibleRowCount(Math.min(myMaxRowCount, myListModel.getSize()));
  }

  protected void afterShow() {
    final int defaultIndex = getListStep().getDefaultOptionIndex();
    if (defaultIndex > 0 && defaultIndex < myList.getModel().getSize()) {
      ListScrollingUtil.selectItem(myList, defaultIndex);
    }
    else {
      selectFirstSelectableItem();
    }

    if (hasSingleSelectableItemWithSubmenu() && getListStep().isAutoSelectionEnabled()) {
      handleSelect(true);
    }
  }

  private void selectFirstSelectableItem() {
    for (int i = 0; i < myListModel.getSize(); i++) {
      if (getListStep().isSelectable(myListModel.getElementAt(i))) {
        myList.setSelectedIndex(i);
        break;
      }
    }
  }

  private boolean hasSingleSelectableItemWithSubmenu() {
    boolean oneSubmenuFound = false;
    int countSelectables = 0;
    for (int i = 0; i < myListModel.getSize(); i++) {
      Object elementAt = myListModel.getElementAt(i);
      if (getListStep().isSelectable(elementAt) ) {
        countSelectables ++;
        if (getStep().hasSubstep(elementAt)) {
          if (oneSubmenuFound) {
            return false;
          }
          oneSubmenuFound = true;
        }
      }
    }
    return oneSubmenuFound && countSelectables == 1;
  }

  protected JComponent createContent() {
    myMouseMotionListener = new MyMouseMotionListener();
    myMouseListener = new MyMouseListener();

    myListModel = new ListPopupModel(this, getListStep());
    myList = new MyList();
    if (myStep.getTitle() != null) {
      myList.getAccessibleContext().setAccessibleName(myStep.getTitle());
    }
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myList.setSelectedIndex(0);
    myList.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    ListScrollingUtil.installActions(myList);

    myList.setCellRenderer(getListElementRenderer());

    myList.getActionMap().get("selectNextColumn").setEnabled(false);
    myList.getActionMap().get("selectPreviousColumn").setEnabled(false);

    registerAction("handleSelection1", KeyEvent.VK_ENTER, 0, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        handleSelect(true);
      }
    });

    registerAction("handleSelection2", KeyEvent.VK_RIGHT, 0, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        handleSelect(false);
      }
    });

    registerAction("goBack2", KeyEvent.VK_LEFT, 0, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (getParent() != null) {
          goBack();
        }
      }
    });


    myList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    return myList;
  }

  protected ActionMap getActionMap() {
    return myList.getActionMap();
  }

  protected InputMap getInputMap() {
    return myList.getInputMap();
  }

  protected ListCellRenderer getListElementRenderer() {
    return new PopupListElementRenderer(this);
  }

  public ListPopupStep getListStep() {
    return (ListPopupStep) myStep;
  }

  protected void dispose() {
    myList.removeMouseMotionListener(myMouseMotionListener);
    myList.removeMouseListener(myMouseListener);
    super.dispose();
  }

  public void disposeChildren() {
    setIndexForShowingChild(-1);
    super.disposeChildren();
  }

  protected void onAutoSelectionTimer() {
    if (myList.getModel().getSize() > 0 && !myList.isSelectionEmpty() ) {
      handleSelect(false);
    }
    else {
      disposeChildren();
      setIndexForShowingChild(-1);
    }
  }

  public void handleSelect(boolean handleFinalChoices) {
    if (myList.getSelectedIndex() == -1) return;

    if (myList.getSelectedIndex() == getIndexForShowingChild()) {
      return;
    }

    if (!getListStep().isSelectable(myList.getSelectedValue())) return;

    if (!getListStep().hasSubstep(myList.getSelectedValue()) && !handleFinalChoices) return;

    disposeChildren();

    if (myListModel.getSize() == 0) {
      disposeAllParents();
      setIndexForShowingChild(-1);
      return;
    }


    handleNextStep(myStep.onChosen(myList.getSelectedValue(), handleFinalChoices), myList.getSelectedValue());
  }

  private void handleNextStep(final PopupStep nextStep, Object parentValue) {
    if (nextStep != PopupStep.FINAL_CHOICE) {
      final Point point = myList.indexToLocation(myList.getSelectedIndex());
      SwingUtilities.convertPointToScreen(point, myList);
      myChild = createPopup(this, nextStep, parentValue);
      myChild.show(getContainer(), point.x + myContainer.getWidth() - STEP_X_PADDING, point.y);
      setIndexForShowingChild(myList.getSelectedIndex());
    }
    else {
      disposeAllParents();
      setIndexForShowingChild(-1);
    }
  }

  public PopupStep getStep() {
    return myStep;
  }

  JList getList() {
    return myList;
  }

  private class MyMouseMotionListener extends MouseMotionAdapter {

    public void mouseMoved(MouseEvent e) {
      Point point = e.getPoint();
      int index = myList.locationToIndex(point);

      if (index != myList.getSelectedIndex()) {
        myList.setSelectedIndex(index);
        restartTimer();
      }

      notifyParentOnChildSelection();
    }
  }

  private class MyMouseListener extends MouseAdapter {
    public void mouseClicked(MouseEvent e) {
      boolean handleFinalChoices = true;
      final Object selectedValue = myList.getSelectedValue();
      final ListPopupStep listStep = getListStep();
      if (listStep.hasSubstep(selectedValue) && listStep.isSelectable(selectedValue)) {
        final int index = myList.getSelectedIndex();
        final Rectangle bounds = myList.getCellBounds(index, index);
        final Point point = e.getPoint();
        if (point.getX() > bounds.width + bounds.getX() - PopupIcons.HAS_NEXT_ICON.getIconWidth()) { //press on handle icon
          handleFinalChoices = false;
        }
      }
      handleSelect(handleFinalChoices);
      stopTimer();
    }
  }

  private static void ensureIndexIsVisible(JList list, int index) {
    Rectangle cellBounds = list.getCellBounds(index, index);
    if (cellBounds != null) {
      list.scrollRectToVisible(cellBounds);
    }
  }

  protected void process(KeyEvent aEvent) {
    myList.processKeyEvent(aEvent);
  }

  private int getIndexForShowingChild() {
    return myIndexForShowingChild;
  }

  private void setIndexForShowingChild(int aIndexForShowingChild) {
    myIndexForShowingChild = aIndexForShowingChild;
  }

  private class MyList extends JList {
    public MyList() {
      super(myListModel);
    }

    public void processKeyEvent(KeyEvent e) {
      e.setSource(this);
      super.processKeyEvent(e);
    }
  }

  protected void onSpeedSearchPatternChanged() {
    myListModel.refilter();
    if (myListModel.getSize() > 0) {
      int fullMatchIndex = myListModel.getClosestMatchIndex();
      if (fullMatchIndex != -1) {
        myList.setSelectedIndex(fullMatchIndex);
      }

      if (myListModel.getSize() <= myList.getSelectedIndex() || !myListModel.isVisible(myList.getSelectedValue())) {
        myList.setSelectedIndex(0);
      }
    }
  }

  protected void onSelectByMnemonic(Object value) {
    myList.setSelectedValue(value, true);
    myList.repaint();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        handleSelect(true);
      }
    });
  }

  public void requestFocus() {
    myList.requestFocus();
  }

  protected void onChildSelectedFor(Object value) {
    if (myList.getSelectedValue() != value) {
      myList.setSelectedValue(value, false);
    }
  }

}
