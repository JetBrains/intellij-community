// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.designSurface;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;


public final class PasteProcessor extends EventProcessor {
  private final GridInsertProcessor myGridInsertProcessor;
  private final PastedComponentList myPastedComponentList;
  private final GuiEditor myEditor;
  private final List<RadComponent> myComponentsToPaste;
  private ComponentDropLocation myLastLocation;
  private final int[] myDX;
  private final int[] myDY;
  private final int myMinRow;
  private final int myMinCol;

  public PasteProcessor(GuiEditor editor, final List<RadComponent> componentsToPaste,
                        final IntList xs, final IntList ys) {
    myEditor = editor;
    myComponentsToPaste = componentsToPaste;
    myGridInsertProcessor = new GridInsertProcessor(editor);
    myPastedComponentList = new PastedComponentList();

    int minX = Integer.MAX_VALUE;
    int minY = Integer.MAX_VALUE;

    // TIntArrayList.min() is broken
    for (int i = 0; i < xs.size(); i++) {
      minX = Math.min(minX, xs.getInt(i));
    }
    for (int i = 0; i < ys.size(); i++) {
      minY = Math.min(minY, ys.getInt(i));
    }

    myDX = new int[xs.size()];
    myDY = new int[ys.size()];
    for(int i=0; i<xs.size(); i++) {
      myDX [i] = xs.getInt(i) - minX;
    }
    for(int i=0; i<ys.size(); i++) {
      myDY [i] = ys.getInt(i) - minY;
    }

    int myMinRow = Integer.MAX_VALUE;
    int myMinCol = Integer.MAX_VALUE;
    for(RadComponent component: myComponentsToPaste) {
      myMinRow = Math.min(myMinRow, component.getConstraints().getRow());
      myMinCol = Math.min(myMinCol, component.getConstraints().getColumn());
    }
    this.myMinRow = myMinRow;
    this.myMinCol = myMinCol;

    StatusBar.Info.set(UIDesignerBundle.message("paste.choose.destination.prompt"), myEditor.getProject());
  }

  @Override
  protected void processKeyEvent(KeyEvent e) {
    if (e.getID() == KeyEvent.KEY_PRESSED) {
      if (e.getKeyCode() == KeyEvent.VK_ENTER) {
        doPaste(myLastLocation);
        e.consume();
      }
      else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
        endPaste();
        e.consume();
      }
      else {
        myLastLocation = moveDropLocation(myEditor, myLastLocation, myPastedComponentList, e);
      }
    }
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    if (e.getID() == MouseEvent.MOUSE_MOVED) {
      myLastLocation = myGridInsertProcessor.processDragEvent(e.getPoint(), myPastedComponentList);
      if (myLastLocation.canDrop(myPastedComponentList)) {
        setCursor(FormEditingUtil.getCopyDropCursor());
      }
      else {
        setCursor(FormEditingUtil.getMoveNoDropCursor());
      }
    }
    else if (e.getID() == MouseEvent.MOUSE_PRESSED) {
      processMousePressed(e);
    }
  }

  private void processMousePressed(final MouseEvent e) {
    ComponentDropLocation location = GridInsertProcessor.getDropLocation(myEditor.getRootContainer(), e.getPoint());
    doPaste(location);
  }

  private void doPaste(final ComponentDropLocation location) {
    if (location.canDrop(myPastedComponentList) && myEditor.ensureEditable()) {
      final RadComponent[] componentsToPaste = myComponentsToPaste.toArray(RadComponent.EMPTY_ARRAY);
      CommandProcessor.getInstance().executeCommand(
        myEditor.getProject(),
        () -> {
          location.processDrop(myEditor, componentsToPaste, null, myPastedComponentList);
          for(RadComponent c: componentsToPaste) {
            FormEditingUtil.iterate(c, new FormEditingUtil.ComponentVisitor() {
              @Override
              public boolean visit(final IComponent component) {
                if (component.getBinding() != null) {
                  InsertComponentProcessor.createBindingField(myEditor, (RadComponent) component);
                }
                return true;
              }
            });
          }
          FormEditingUtil.selectComponents(myEditor, myComponentsToPaste);
          myEditor.refreshAndSave(true);
        }, UIDesignerBundle.message("command.paste"), null);
      endPaste();
    }
  }

  private void endPaste() {
    myEditor.getMainProcessor().stopCurrentProcessor();
    myEditor.getActiveDecorationLayer().removeFeedback();
    WindowManager.getInstance().getStatusBar(myEditor.getProject()).setInfo("");
  }

  @Override
  protected boolean cancelOperation() {
    WindowManager.getInstance().getStatusBar(myEditor.getProject()).setInfo("");
    return true;
  }

  @Override public boolean needMousePressed() {
    return true;
  }

  private class PastedComponentList implements ComponentDragObject {
    @Override
    public int getComponentCount() {
      return myComponentsToPaste.size();
    }

    @Override
    public boolean isHGrow() {
      return false;
    }

    @Override
    public boolean isVGrow() {
      return false;
    }

    @Override
    public int getRelativeRow(int componentIndex) {
      return myComponentsToPaste.get(componentIndex).getConstraints().getRow() - myMinRow;
    }

    @Override
    public int getRelativeCol(int componentIndex) {
      return myComponentsToPaste.get(componentIndex).getConstraints().getColumn() - myMinCol;
    }

    @Override
    public int getRowSpan(int componentIndex) {
      return myComponentsToPaste.get(componentIndex).getConstraints().getRowSpan();
    }

    @Override
    public int getColSpan(int componentIndex) {
      return myComponentsToPaste.get(componentIndex).getConstraints().getColSpan();
    }

    @Override
    public Point getDelta(int componentIndex) {
      return new Point(myDX [componentIndex], myDY [componentIndex]);
    }

    @Override
    public @NotNull Dimension getInitialSize(final RadContainer targetContainer) {
      if (myComponentsToPaste.size() == 1) {
        return myComponentsToPaste.get(0).getSize();
      }
      return new Dimension(-1, -1);
    }
  }
}
