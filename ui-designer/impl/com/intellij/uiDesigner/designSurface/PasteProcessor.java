/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.radComponents.RadComponent;
import gnu.trove.TIntArrayList;

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
  private GridLocation myLastLocation;
  private int[] myDX;
  private int[] myDY;

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

    final StatusBar statusBar = WindowManager.getInstance().getStatusBar(myEditor.getProject());
    statusBar.setInfo(UIDesignerBundle.message("paste.choose.destination.prompt"));
  }

  protected void processKeyEvent(KeyEvent e) {
    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
      doPaste(myLastLocation);
    }
    else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
      endPaste();
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
    GridLocation location = GridInsertProcessor.getGridInsertLocation(myEditor, e.getPoint(),
                                                                      myPastedComponentList);
    doPaste(location);
  }

  private void doPaste(final GridLocation location) {
    if (location.canDrop(myPastedComponentList)) {
      RadComponent[] componentsToPaste = myComponentsToPaste.toArray(new RadComponent[myComponentsToPaste.size()]);
      location.processDrop(myEditor, componentsToPaste, null, myDX, myDY);
      FormEditingUtil.clearSelection(myEditor.getRootContainer());
      for(RadComponent c: myComponentsToPaste) {
        c.setSelected(true);
      }
      endPaste();
    }
  }

  private void endPaste() {
    myEditor.getMainProcessor().stopCurrentProcessor();
    myEditor.getActiveDecorationLayer().removeFeedback();
    WindowManager.getInstance().getStatusBar(myEditor.getProject()).setInfo("");
  }

  protected boolean cancelOperation() {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override public boolean needMousePressed() {
    return true;
  }

  private class PastedComponentList implements ComponentDragObject {
    public int getComponentCount() {
      return myComponentsToPaste.size();
    }

    public int getDragRelativeColumn() {
      return 0;
    }

    public int getHSizePolicy() {
      return 0;
    }

    public int getVSizePolicy() {
      return 0;
    }
  }
}
