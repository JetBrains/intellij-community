package com.intellij.uiDesigner;

import com.intellij.openapi.actionSystem.*;
import com.intellij.uiDesigner.actions.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class GlassLayer extends JComponent implements DataProvider{
  private final GuiEditor myEditor;

  public GlassLayer(final GuiEditor editor){
    myEditor = editor;
    enableEvents(AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);

    final MoveSelectionToRightAction moveSelectionToRightAction = new MoveSelectionToRightAction(myEditor);
    moveSelectionToRightAction.registerCustomShortcutSet(
      ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT).getShortcutSet(),
      this
    );

    final MoveSelectionToLeftAction moveSelectionToLeftAction = new MoveSelectionToLeftAction(myEditor);
    moveSelectionToLeftAction.registerCustomShortcutSet(
      ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT).getShortcutSet(),
      this
    );

    final MoveSelectionToUpAction moveSelectionToUpAction = new MoveSelectionToUpAction(myEditor);
    moveSelectionToUpAction.registerCustomShortcutSet(
      ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP).getShortcutSet(),
      this
    );

    final MoveSelectionToDownAction moveSelectionToDownAction = new MoveSelectionToDownAction(myEditor);
    moveSelectionToDownAction.registerCustomShortcutSet(
      ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN).getShortcutSet(),
      this
    );

    // F2 should start inplace editing
    final StartInplaceEditingAction startInplaceEditingAction = new StartInplaceEditingAction(editor);
    startInplaceEditingAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)),
      this
    );
  }

  protected void processKeyEvent(final KeyEvent e){
    myEditor.myProcessor.processKeyEvent(e);
  }

  protected void processMouseEvent(final MouseEvent e){
    if(e.getID() == MouseEvent.MOUSE_PRESSED){
      requestFocusInWindow();
    }
    myEditor.myProcessor.processMouseEvent(e);
  }

  protected void processMouseMotionEvent(final MouseEvent e){
    myEditor.myProcessor.processMouseEvent(e);
  }

  /**
   * Provides {@link DataConstants#NAVIGATABLE} to navigate to
   * binding of currently selected component (if any)
   */
  public Object getData(final String dataId) {
    if(DataConstants.NAVIGATABLE.equals(dataId)){
      return myEditor.getComponentTree().getData(dataId);
    }
    else{
      return null;
    }
  }
}
