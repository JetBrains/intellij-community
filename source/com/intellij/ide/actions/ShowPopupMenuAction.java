package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.editor.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author Vladimir Kondratyev
 */
public class ShowPopupMenuAction extends AnAction{
  public ShowPopupMenuAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e){
    KeyboardFocusManager focusManager=KeyboardFocusManager.getCurrentKeyboardFocusManager();
    Component focusOwner=focusManager.getFocusOwner();
    Point popupMenuPoint = getPopupLocation(focusOwner, e.getDataContext());

    focusOwner.dispatchEvent(
      new MouseEvent(
        focusOwner,
        MouseEvent.MOUSE_PRESSED,
        System.currentTimeMillis(),0,
        popupMenuPoint.x,
        popupMenuPoint.y,
        1,
        true
      )
    );
  }

  /**
   * @return location as close as possible to the action origin. Method has special handling of
   * the following components:<br>
   *   - caret offset for editor<br>
   *   - current selected node for tree<br>
   *   - current selected row for list<br>
   * <br>
   * The returned point is in <code>focusOwner</code> coordinate system.
   *
   * @exception java.lang.IllegalArgumentException if <code>focusOwner</code>
   * is <code>null</code>
   */
  public static Point getPopupLocation(Component focusOwner, DataContext dataContext) {
    if (focusOwner == null) {
      throw new IllegalArgumentException("focusOwner cannot be null");
    }
    final Rectangle visibleRect;
    if(focusOwner instanceof JComponent){
      visibleRect = ((JComponent)focusOwner).getVisibleRect();
    }
    else{
      visibleRect = new Rectangle(0, 0, focusOwner.getWidth(), focusOwner.getHeight());
    }
    Point popupMenuPoint=null;

    if(focusOwner instanceof EditorComponentImpl){ // Editor
      Editor editor=(Editor)dataContext.getData(DataConstants.EDITOR);
      LogicalPosition logicalPosition=editor.getCaretModel().getLogicalPosition();
      Point p=editor.logicalPositionToXY(logicalPosition);
      if(visibleRect.contains(p)){
        popupMenuPoint= p;
      }
    }else if(focusOwner instanceof JList){ // JList
      JList list=(JList)focusOwner;
      int firstVisibleIndex=list.getFirstVisibleIndex();
      int lastVisibleIndex=list.getLastVisibleIndex();
      int[] selectedIndices=list.getSelectedIndices();
      for(int i=0;i<selectedIndices.length;i++){
        int index=selectedIndices[i];
        if(firstVisibleIndex<=index&&index<=lastVisibleIndex){
          Rectangle cellBounds=list.getCellBounds(index,index);
          popupMenuPoint=new Point(visibleRect.x+visibleRect.width/4,cellBounds.y+cellBounds.height/2);
          break;
        }
      }
    }else if(focusOwner instanceof JTree){ // JTree
      JTree tree=(JTree)focusOwner;
      int[] selectionRows=tree.getSelectionRows();
      for(int i=0;selectionRows != null && i<selectionRows.length;i++){
        int row=selectionRows[i];
        Rectangle rowBounds=tree.getRowBounds(row);
        if(visibleRect.y<=rowBounds.y && rowBounds.y<=visibleRect.y+visibleRect.height){
          popupMenuPoint=new Point(visibleRect.x+visibleRect.width/4,rowBounds.y+rowBounds.height/2);
          break;
        }
      }
    }
    // TODO[vova] add usability for JTable
    if(popupMenuPoint==null){
      popupMenuPoint=new Point(visibleRect.x+visibleRect.width/2,visibleRect.y+visibleRect.height/2);
    }
    return popupMenuPoint;
  }

  public void update(AnActionEvent e){
    KeyboardFocusManager focusManager=KeyboardFocusManager.getCurrentKeyboardFocusManager();
    e.getPresentation().setEnabled(focusManager.getFocusOwner() instanceof JComponent);
  }
}