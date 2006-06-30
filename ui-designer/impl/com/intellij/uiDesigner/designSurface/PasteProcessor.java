/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.radComponents.RadComponent;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

/**
 * @author yole
 */
public class PasteProcessor extends EventProcessor {
  private GridInsertProcessor myGridInsertProcessor;
  private PastedComponentList myPastedComponentList;
  private final GuiEditor myEditor;
  private final ArrayList<RadComponent> myComponentsToPaste;
  private DropLocation myLastLocation;
  private int[] myDX;
  private int[] myDY;
  private int myMinRow;
  private int myMinCol;

  public PasteProcessor(GuiEditor editor, final ArrayList<RadComponent> componentsToPaste,
                        final TIntArrayList xs, final TIntArrayList ys) {
    myEditor = editor;
    myComponentsToPaste = componentsToPaste;
    myGridInsertProcessor = new GridInsertProcessor(editor);
    myPastedComponentList = new PastedComponentList();

    int minX = Integer.MAX_VALUE;
    int minY = Integer.MAX_VALUE;

    // TIntArrayList.min() is broken
    for(int i=0; i<xs.size(); i++) {
      minX = Math.min(minX, xs.get(i));
    }
    for(int i=0; i<ys.size(); i++) {
      minY = Math.min(minY, ys.get(i));
    }

    myDX = new int[xs.size()];
    myDY = new int[ys.size()];
    for(int i=0; i<xs.size(); i++) {
      myDX [i] = xs.get(i) - minX;
    }
    for(int i=0; i<ys.size(); i++) {
      myDY [i] = ys.get(i) - minY;
    }

    myMinRow = Integer.MAX_VALUE;
    myMinCol = Integer.MAX_VALUE;
    for(RadComponent component: myComponentsToPaste) {
      myMinRow = Math.min(myMinRow, component.getConstraints().getRow());
      myMinCol = Math.min(myMinCol, component.getConstraints().getColumn());
    }

    final StatusBar statusBar = WindowManager.getInstance().getStatusBar(myEditor.getProject());
    statusBar.setInfo(UIDesignerBundle.message("paste.choose.destination.prompt"));
  }

  protected void processKeyEvent(KeyEvent e) {
    if (e.getID() == KeyEvent.KEY_PRESSED) {
      if (e.getKeyCode() == KeyEvent.VK_ENTER) {
        doPaste(myLastLocation);
      }
      else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
        endPaste();
      }
      else {
        myLastLocation = moveDropLocation(myEditor, myLastLocation, myPastedComponentList, e);
      }
    }
  }

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
    DropLocation location = GridInsertProcessor.getDropLocation(myEditor.getRootContainer(), e.getPoint()
    );
    doPaste(location);
  }

  private void doPaste(final DropLocation location) {
    if (location.canDrop(myPastedComponentList)) {
      RadComponent[] componentsToPaste = myComponentsToPaste.toArray(new RadComponent[myComponentsToPaste.size()]);
      location.processDrop(myEditor, componentsToPaste, null, myPastedComponentList);
      for(RadComponent c: componentsToPaste) {
        FormEditingUtil.iterate(c, new FormEditingUtil.ComponentVisitor() {
          public boolean visit(final IComponent component) {
            if (component.getBinding() != null) {
              InsertComponentProcessor.createBindingField(myEditor, (RadComponent) component);
            }
            return true;
          }
        });
      }
      myEditor.refreshAndSave(true);
      FormEditingUtil.selectComponents(myEditor, myComponentsToPaste);
      endPaste();
    }
  }

  private void endPaste() {
    myEditor.getMainProcessor().stopCurrentProcessor();
    myEditor.getActiveDecorationLayer().removeFeedback();
    WindowManager.getInstance().getStatusBar(myEditor.getProject()).setInfo("");
  }

  protected boolean cancelOperation() {
    WindowManager.getInstance().getStatusBar(myEditor.getProject()).setInfo("");
    return true;
  }

  @Override public boolean needMousePressed() {
    return true;
  }

  private class PastedComponentList implements ComponentDragObject {
    public int getComponentCount() {
      return myComponentsToPaste.size();
    }

    public boolean isHGrow() {
      return false;
    }

    public boolean isVGrow() {
      return false;
    }

    public int getRelativeRow(int componentIndex) {
      return myComponentsToPaste.get(componentIndex).getConstraints().getRow() - myMinRow;
    }

    public int getRelativeCol(int componentIndex) {
      return myComponentsToPaste.get(componentIndex).getConstraints().getColumn() - myMinCol;
    }

    public int getRowSpan(int componentIndex) {
      return myComponentsToPaste.get(componentIndex).getConstraints().getRowSpan();
    }

    public int getColSpan(int componentIndex) {
      return myComponentsToPaste.get(componentIndex).getConstraints().getColSpan();
    }

    public Point getDelta(int componentIndex) {
      return new Point(myDX [componentIndex], myDY [componentIndex]);
    }

    @NotNull
    public Dimension getInitialSize(final JComponent parent) {
      if (myComponentsToPaste.size() == 1) {
        return myComponentsToPaste.get(0).getSize();
      }
      return new Dimension(-1, -1);
    }
  }
}
