
package com.intellij.ui.plaf.beg;

import java.awt.event.MouseEvent;
import javax.swing.*;
import javax.swing.event.MouseInputListener;
import javax.swing.plaf.basic.BasicListUI;

public class BegListUI extends BasicListUI {
  protected MouseInputListener createMouseInputListener() {
    return new PatchedInputHandler(this.list);
  }

  protected int convertYToRow(int y) {
    return super.convertYToRow(y);
  }

  public class PatchedInputHandler extends MouseInputHandler {
    private JList myList;

    PatchedInputHandler(JList list) {
      myList = list;
    }

    public void mousePressed(MouseEvent e) {
      if (!myList.isEnabled()){
        return;
      }

      /* Request focus before updating the list selection.  This implies
       * that the current focus owner will see a focusLost() event
       * before the lists selection is updated IF requestFocus() is
       * synchronous (it is on Windows).  See bug 4122345
       */
      if (!myList.hasFocus()){
        myList.requestFocus();
      }

      int row = BegListUI.this.convertYToRow(e.getY());
      if (row != -1){
        myList.setValueIsAdjusting(true);
        int anchorIndex = myList.getAnchorSelectionIndex();
        if (e.isControlDown()){
          if (myList.isSelectedIndex(row)){
            myList.removeSelectionInterval(row, row);
          }
          else{
            myList.addSelectionInterval(row, row);
          }
        }
        else
          if (e.isShiftDown() && (anchorIndex != -1)){
            myList.setSelectionInterval(anchorIndex, row);
          }
          else{
            myList.setSelectionInterval(row, row);
          }
      }
    }

    public void mouseReleased(MouseEvent e) {
      myList.setValueIsAdjusting(false);
    }
  }
}