package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.ui.ScrollingUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public class RunAnythingScrollingUtil {
  @NonNls
  protected static final String SELECT_PREVIOUS_ROW_ACTION_ID = "selectPreviousRow";
  @NonNls
  protected static final String SELECT_NEXT_ROW_ACTION_ID = "selectNextRow";

  public static void installActions(@NotNull JList list, @NotNull JTextField focusParent, @NotNull Runnable handleFocusParent) {
    ActionMap actionMap = list.getActionMap();
    actionMap.put(SELECT_PREVIOUS_ROW_ACTION_ID, new MoveAction(SELECT_PREVIOUS_ROW_ACTION_ID, list, handleFocusParent));
    actionMap.put(SELECT_NEXT_ROW_ACTION_ID, new MoveAction(SELECT_NEXT_ROW_ACTION_ID, list, handleFocusParent));

    maybeInstallDefaultShortcuts(list);

    installMoveUpAction(list, focusParent, handleFocusParent);
    installMoveDownAction(list, focusParent, handleFocusParent);
  }

  private static void maybeInstallDefaultShortcuts(@NotNull JComponent component) {
    InputMap map = component.getInputMap(JComponent.WHEN_FOCUSED);
    UIUtil.maybeInstall(map, SELECT_PREVIOUS_ROW_ACTION_ID, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0));
    UIUtil.maybeInstall(map, SELECT_NEXT_ROW_ACTION_ID, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0));
  }

  private static void installMoveDownAction(@NotNull JList list, @NotNull JComponent focusParent, @NotNull Runnable handleFocusParent) {
    new ScrollingUtil.ListScrollAction(CommonShortcuts.getMoveDown(), focusParent) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        moveDown(list, handleFocusParent);
      }
    };
  }

  private static void installMoveUpAction(@NotNull JList list, @NotNull JComponent focusParent, @NotNull Runnable handleFocusParent) {
    new ScrollingUtil.ListScrollAction(CommonShortcuts.getMoveUp(), focusParent) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        moveUp(list, handleFocusParent);
      }
    };
  }

  private static void moveDown(@NotNull JList list, @NotNull Runnable handleFocusParent) {
    move(list, list.getSelectionModel(), list.getModel().getSize(), +1, handleFocusParent);
  }

  private static void moveUp(@NotNull JList list, @NotNull Runnable handleFocusParent) {
    move(list, list.getSelectionModel(), list.getModel().getSize(), -1, handleFocusParent);
  }

  private static void move(@NotNull JList c,
                           @NotNull ListSelectionModel selectionModel,
                           int size,
                           int direction,
                           @NotNull Runnable handleFocusParent) {
    if (size == 0) return;
    int index = selectionModel.getMaxSelectionIndex();
    int indexToSelect = index + direction;

    if (indexToSelect == -2) {
      indexToSelect = size - 1;
    }
    else if (indexToSelect == -1 || indexToSelect >= size) {
      handleFocusParent.run();
      return;
    }

    ScrollingUtil.ensureIndexIsVisible(c, indexToSelect, -1);
    selectionModel.setSelectionInterval(indexToSelect, indexToSelect);
  }

  private static class MoveAction extends AbstractAction {
    @NotNull private final String myId;
    @NotNull private final JList myComponent;
    @NotNull private final Runnable myHandleFocusParent;

    public MoveAction(@NotNull String id, @NotNull JList component, @NotNull Runnable handleFocusParent) {
      myId = id;
      myComponent = component;
      myHandleFocusParent = handleFocusParent;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (SELECT_PREVIOUS_ROW_ACTION_ID.equals(myId)) {
        moveUp(myComponent, myHandleFocusParent);
      }
      else if (SELECT_NEXT_ROW_ACTION_ID.equals(myId)) moveDown(myComponent, myHandleFocusParent);
    }
  }
}