package com.intellij.openapi.editor.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.InputMethodEvent;
import java.awt.im.InputMethodRequests;

/**
 *
 */
public class EditorComponentImpl extends JComponent implements Scrollable, DataProvider {
  private EditorImpl myEditor;

  public EditorComponentImpl(EditorImpl editor) {
    myEditor = editor;
    enableEvents(AWTEvent.KEY_EVENT_MASK | AWTEvent.INPUT_METHOD_EVENT_MASK);
    enableInputMethods(true);
    setFocusCycleRoot(true);
    setOpaque(true);
  }

  public EditorImpl getEditor() {
    return myEditor;
  }


  public Object getData(String dataId) {
    if (myEditor.isRendererMode()) return null;

    if (DataConstants.EDITOR.equals(dataId)) {
      if (myEditor.getVirtualFile() == null) {
        return myEditor;
      }
      else {
        return null;
      }
    }

    if (DataConstantsEx.DELETE_ELEMENT_PROVIDER.equals(dataId)) {
      return myEditor.getDeleteProvider();
    }
    if (DataConstantsEx.CUT_PROVIDER.equals(dataId)) {
      return myEditor.getCutProvider();
    }
    if (DataConstantsEx.COPY_PROVIDER.equals(dataId)) {
      return myEditor.getCopyProvider();
    }
    if (DataConstantsEx.PASTE_PROVIDER.equals(dataId)) {
      return myEditor.getPasteProvider();
    }

    return null;
  }

  public Dimension getPreferredSize() {
    return myEditor.getPreferredSize();
  }

  protected void fireResized() {
    processComponentEvent(new ComponentEvent(this, ComponentEvent.COMPONENT_RESIZED));
  }

  protected void processInputMethodEvent(InputMethodEvent e) {
    super.processInputMethodEvent(e);
    if (!e.isConsumed()) {
      switch (e.getID()) {
        case InputMethodEvent.INPUT_METHOD_TEXT_CHANGED:
          myEditor.replaceInputMethodText(e);
          // No breaks over here.

        case InputMethodEvent.CARET_POSITION_CHANGED:
          myEditor.inputMethodCaretPositionChanged(e);
          break;
      }
      e.consume();
    }
  }

  public InputMethodRequests getInputMethodRequests() {
    return myEditor.getInputMethodRequests();
  }

  public void paintComponent(Graphics g) {
    ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintStart();

    try {
      ((Graphics2D)g).setComposite(AlphaComposite.Src);

      UISettings.setupAntialiasing(g);
      myEditor.paint(g);
    }
    finally {
      ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintFinish();
    }
  }

  public void repaintEditorComponent() {
    repaint();
  }

  public void repaintEditorComponent(int x, int y, int width, int height) {
    repaint(x, y, width, height);
  }

  //--implementation of Scrollable interface--------------------------------------
  public Dimension getPreferredScrollableViewportSize() {
    return myEditor.getPreferredSize();
  }

  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    if (orientation == SwingConstants.VERTICAL) {
      return myEditor.getLineHeight();
    }
    else { // if orientation == SwingConstants.HORIZONTAL
      return myEditor.getSpaceWidth(Font.PLAIN);
    }
  }

  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    if (orientation == SwingConstants.VERTICAL) {
      int lineHeight = myEditor.getLineHeight();
      if (direction > 0) {
        int lineNumber = (visibleRect.y + visibleRect.height) / lineHeight;
        return lineHeight * lineNumber - visibleRect.y;
      }
      else {
        int lineNumber = (visibleRect.y - visibleRect.height) / lineHeight;
        return visibleRect.y - lineHeight * lineNumber;
      }
    }
    else { // if orientation == SwingConstants.HORIZONTAL
      return visibleRect.width;
    }
  }

  public boolean getScrollableTracksViewportWidth() {
    return getParent()instanceof JViewport && getParent().getWidth() > getPreferredSize().width;
  }

  public boolean getScrollableTracksViewportHeight() {
    return getParent()instanceof JViewport && getParent().getHeight() > getPreferredSize().height;
  }
}
