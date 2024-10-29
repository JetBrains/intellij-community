// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.designSurface;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.ui.popup.PopupOwner;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.actions.*;
import com.intellij.uiDesigner.componentTree.ComponentTree;
import com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

public final class GlassLayer extends JComponent implements UiDataProvider, PopupOwner {
  private static final Logger LOG = Logger.getInstance(GlassLayer.class);

  private final GuiEditor myEditor;
  private Point myLastMousePosition;

  public GlassLayer(GuiEditor editor) {
    myEditor = editor;
    enableEvents(AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);

    registerKeyboardAction(new MoveSelectionToRightAction(myEditor, false, false), IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT);
    registerKeyboardAction(new MoveSelectionToLeftAction(myEditor, false, false), IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT);
    registerKeyboardAction(new MoveSelectionToUpAction(myEditor, false, false), IdeActions.ACTION_EDITOR_MOVE_CARET_UP);
    registerKeyboardAction(new MoveSelectionToDownAction(myEditor, false, false), IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);

    registerKeyboardAction(new MoveSelectionToRightAction(myEditor, true, false), "EditorRightWithSelection");
    registerKeyboardAction(new MoveSelectionToLeftAction(myEditor, true, false), "EditorLeftWithSelection");
    registerKeyboardAction(new MoveSelectionToUpAction(myEditor, true, false), "EditorUpWithSelection");
    registerKeyboardAction(new MoveSelectionToDownAction(myEditor, true, false), "EditorDownWithSelection");

    registerKeyboardAction(new MoveSelectionToRightAction(myEditor, false, true), "EditorLineEnd");
    registerKeyboardAction(new MoveSelectionToLeftAction(myEditor, false, true), "EditorLineStart");
    registerKeyboardAction(new MoveSelectionToUpAction(myEditor, false, true), "EditorPageUp");
    registerKeyboardAction(new MoveSelectionToDownAction(myEditor, false, true), "EditorPageDown");

    registerKeyboardAction(new MoveSelectionToRightAction(myEditor, true, true), "EditorLineEndWithSelection");
    registerKeyboardAction(new MoveSelectionToLeftAction(myEditor, true, true), "EditorLineStartWithSelection");
    registerKeyboardAction(new MoveSelectionToUpAction(myEditor, true, true), "EditorPageUpWithSelection");
    registerKeyboardAction(new MoveSelectionToDownAction(myEditor, true, true), "EditorPageDownWithSelection");

    registerKeyboardAction(new MoveComponentAction(-1, 0, 0, 0), "EditorScrollUp");
    registerKeyboardAction(new MoveComponentAction(1, 0, 0, 0), "EditorScrollDown");
    registerKeyboardAction(new MoveComponentAction(0, -1, 0, 0), "EditorPreviousWord");
    registerKeyboardAction(new MoveComponentAction(0, 1, 0, 0), "EditorNextWord");

    registerKeyboardAction(new MoveComponentAction(0, 0, -1, 0), IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION);
    registerKeyboardAction(new MoveComponentAction(0, 0, 1, 0), IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION);
    registerKeyboardAction(new MoveComponentAction(0, 0, 0, -1), "EditorPreviousWordWithSelection");
    registerKeyboardAction(new MoveComponentAction(0, 0, 0, 1), "EditorNextWordWithSelection");

    registerKeyboardAction(new SelectAllComponentsAction(), "$SelectAll");

    // F2 should start inplace editing
    final StartInplaceEditingAction startInplaceEditingAction = new StartInplaceEditingAction(editor);
    startInplaceEditingAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)),
      this
    );
  }

  private void registerKeyboardAction(final AnAction action, final @NonNls String actionId) {
    action.registerCustomShortcutSet(
      ActionManager.getInstance().getAction(actionId).getShortcutSet(),
      this
    );
  }

  @Override
  protected void processKeyEvent(final KeyEvent e) {
    myEditor.myProcessor.processKeyEvent(e);
    if (!e.isConsumed()) {
      super.processKeyEvent(e);
    }
  }

  @Override
  protected void processMouseEvent(final MouseEvent e) {
    if (e.getID() == MouseEvent.MOUSE_PRESSED) {
      requestFocusInWindow();
    }
    try {
      myEditor.myProcessor.processMouseEvent(e);
    }
    catch (ProcessCanceledException ex) {
      throw ex;
    }
    catch (Exception ex) {
      LOG.error(ex);
    }
  }

  @Override
  protected void processMouseMotionEvent(final MouseEvent e) {
    myLastMousePosition = e.getPoint();
    try {
      myEditor.myProcessor.processMouseEvent(e);
    }
    catch (ProcessCanceledException ex) {
      throw ex;
    }
    catch (Exception ex) {
      LOG.error(ex);
    }
  }

  public @NotNull Point getLastMousePosition() {
    if (myLastMousePosition == null) {
      return new Point(10, 10);
    }
    return myLastMousePosition;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    ComponentTree componentTree = DesignerToolWindowManager.getInstance(myEditor).getComponentTree();
    RadComponent radComponent = componentTree == null ? null : componentTree.getSelectedComponent();
    if (radComponent == null) return;
    sink.lazy(CommonDataKeys.NAVIGATABLE, () -> {
      return componentTree.getPsiFile(radComponent);
    });
  }

  @Override
  public @Nullable Point getBestPopupPosition() {
    final ArrayList<RadComponent> selection = FormEditingUtil.getSelectedComponents(myEditor);
    if (!selection.isEmpty()) {
      final RadComponent component = selection.get(0);
      final Rectangle bounds = component.getBounds();
      int bottom = bounds.height > 4 ? bounds.y + bounds.height - 4 : bounds.y;
      int left = bounds.width > 4 ? bounds.x + 4 : bounds.x;
      Point pnt = new Point(left, bottom);  // the location needs to be within the component
      return SwingUtilities.convertPoint(component.getParent().getDelegee(), pnt, this);
    }
    return null;
  }
}
